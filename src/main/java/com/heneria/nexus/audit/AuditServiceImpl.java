package com.heneria.nexus.audit;

import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.db.repository.AuditLogRepository;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.NamedThreadFactory;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation buffering audit entries before persisting them in MariaDB.
 */
public final class AuditServiceImpl implements AuditService, LifecycleAware {

    private static final int FLUSH_THRESHOLD = 32;
    private static final int MAX_BATCH_SIZE = 256;
    private static final int MAX_BUFFER_SIZE = 2_000;

    private final NexusLogger logger;
    private final AuditLogRepository repository;
    private final ExecutorManager executorManager;
    private final Duration flushInterval;
    private final Queue<AuditLogRecord> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingSize = new AtomicInteger();
    private final AtomicBoolean flushing = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean overflowWarned = new AtomicBoolean();
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private volatile ScheduledExecutorService scheduler;

    public AuditServiceImpl(NexusLogger logger,
                            AuditLogRepository repository,
                            ExecutorManager executorManager,
                            CoreConfig coreConfig) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        Objects.requireNonNull(coreConfig, "coreConfig");
        this.flushInterval = coreConfig.databaseSettings().writeBehindInterval();
    }

    @Override
    public CompletableFuture<Void> start() {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        long intervalMs = Math.max(1000L, flushInterval.toMillis());
        ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("Nexus-Audit", true, logger));
        executor.scheduleAtFixedRate(this::flushSilently, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        scheduler = executor;
        logger.info("Journal d'audit initialisé (intervalle=%d ms)".formatted(intervalMs));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        running.set(false);
        ScheduledExecutorService executor = scheduler;
        if (executor != null) {
            executor.shutdownNow();
            scheduler = null;
        }
        return flushAll();
    }

    @Override
    public void log(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        AuditLogRecord record = new AuditLogRecord(
                0L,
                Instant.now(),
                entry.actorUuid(),
                entry.actorName(),
                entry.actionType(),
                entry.targetUuid(),
                entry.targetName(),
                entry.details());
        pending.add(record);
        int size = pendingSize.incrementAndGet();
        if (size >= MAX_BUFFER_SIZE && overflowWarned.compareAndSet(false, true)) {
            logger.warn("File de journal d'audit saturée (%d entrées).".formatted(size));
        }
        if (size >= FLUSH_THRESHOLD) {
            flushSilently();
        }
    }

    @Override
    public CompletableFuture<AuditLogPage> query(AuditLogQuery query) {
        Objects.requireNonNull(query, "query");
        int fetchSize = Math.min(MAX_BATCH_SIZE, query.pageSize() + 1);
        Optional<UUID> subjectUuid = query.subjectUuid();
        Optional<String> subjectName = query.subjectName();
        return repository.findRecent(subjectUuid, subjectName, fetchSize, query.offset())
                .thenApply(records -> {
                    boolean hasNext = records.size() > query.pageSize();
                    List<AuditLogRecord> trimmed = records;
                    if (hasNext) {
                        trimmed = records.subList(0, query.pageSize());
                    }
                    return new AuditLogPage(query.page(), query.pageSize(), hasNext, List.copyOf(trimmed));
                });
    }

    @Override
    public boolean isHealthy() {
        return lastError.get() == null;
    }

    @Override
    public Optional<Throwable> lastError() {
        return Optional.ofNullable(lastError.get());
    }

    private void flushSilently() {
        if (!running.get() && pending.isEmpty()) {
            return;
        }
        triggerFlush(false);
    }

    private CompletableFuture<Void> flushAll() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executorManager.runIo(() -> triggerFlush(true))
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else if (pending.isEmpty()) {
                        future.complete(null);
                    } else {
                        triggerFlush(true);
                        future.complete(null);
                    }
                });
        return future;
    }

    private void triggerFlush(boolean blocking) {
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        List<AuditLogRecord> batch = drainBatch();
        if (batch.isEmpty()) {
            flushing.set(false);
            return;
        }
        CompletableFuture<Void> future = repository.saveAll(batch);
        if (blocking) {
            future = future.whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    handleFlushFailure(batch, throwable);
                } else {
                    lastError.set(null);
                }
            });
            try {
                future.join();
            } catch (Exception exception) {
                flushing.set(false);
                throw exception;
            }
            flushing.set(false);
            if (!pending.isEmpty()) {
                triggerFlush(blocking);
            }
            return;
        }
        future.whenComplete((ignored, throwable) -> {
            try {
                if (throwable != null) {
                    handleFlushFailure(batch, throwable);
                } else {
                    lastError.set(null);
                }
            } finally {
                flushing.set(false);
            }
            if (!pending.isEmpty()) {
                flushSilently();
            }
        });
    }

    private List<AuditLogRecord> drainBatch() {
        ArrayList<AuditLogRecord> batch = new ArrayList<>();
        while (batch.size() < MAX_BATCH_SIZE) {
            AuditLogRecord record = pending.poll();
            if (record == null) {
                break;
            }
            pendingSize.decrementAndGet();
            batch.add(record);
        }
        return batch;
    }

    private void handleFlushFailure(List<AuditLogRecord> batch, Throwable throwable) {
        lastError.set(throwable);
        logger.error("Erreur lors de la persistance du journal d'audit", throwable);
        for (AuditLogRecord record : batch) {
            pending.add(record);
            pendingSize.incrementAndGet();
        }
    }
}
