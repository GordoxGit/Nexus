package com.heneria.nexus.watchdog;

import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.util.NamedThreadFactory;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Default implementation of the watchdog service.
 */
public final class WatchdogServiceImpl implements WatchdogService {

    private static final int MAX_REPORTS = 20;

    private final NexusLogger logger;
    private final ExecutorManager executorManager;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<String, Consumer<Throwable>> fallbacks = new ConcurrentHashMap<>();
    private final Deque<WatchdogReport> reports = new ArrayDeque<>();
    private final Object reportsLock = new Object();
    private final LongAdder monitored = new LongAdder();
    private final LongAdder timedOut = new LongAdder();
    private final LongAdder totalDurationMillis = new LongAdder();
    private final AtomicBoolean stopped = new AtomicBoolean();

    public WatchdogServiceImpl(NexusLogger logger, ExecutorManager executorManager) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                new NamedThreadFactory("Nexus-Watchdog", true, logger));
        executor.setRemoveOnCancelPolicy(true);
        this.scheduler = executor;
    }

    @Override
    public <T> CompletableFuture<T> monitor(String taskName, Duration timeout, Supplier<CompletableFuture<T>> taskSupplier) {
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(taskSupplier, "taskSupplier");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (stopped.get()) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Watchdog service stopped"));
            return failed;
        }
        monitored.increment();
        Instant start = Instant.now();
        CompletableFuture<T> task;
        try {
            task = Objects.requireNonNull(taskSupplier.get(), "taskSupplier returned null future");
        } catch (Throwable throwable) {
            recordFailure(taskName, start, throwable);
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(throwable);
            fallbacks.remove(taskName);
            return failed;
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        AtomicBoolean recorded = new AtomicBoolean();
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            if (!recorded.compareAndSet(false, true)) {
                return;
            }
            Instant now = Instant.now();
            Duration duration = Duration.between(start, now);
            WatchdogTimeoutException exception = new WatchdogTimeoutException(taskName, timeout);
            timedOut.increment();
            totalDurationMillis.add(Math.max(0L, duration.toMillis()));
            logger.warn("[Watchdog] Tâche '" + taskName + "' a dépassé le délai de " + duration.toMillis()
                    + " ms ! Tentative d'annulation...");
            if (!task.isDone()) {
                task.cancel(true);
            }
            recordReport(new WatchdogReport(taskName, now, duration, WatchdogReport.Status.TIMED_OUT, Optional.of(exception)));
            result.completeExceptionally(exception);
            executeFallback(taskName, exception);
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        task.whenComplete((value, throwable) -> {
            timeoutFuture.cancel(false);
            if (!recorded.compareAndSet(false, true)) {
                return;
            }
            Instant now = Instant.now();
            Duration duration = Duration.between(start, now);
            totalDurationMillis.add(Math.max(0L, duration.toMillis()));
            fallbacks.remove(taskName);
            if (throwable == null) {
                logger.info("[Watchdog] Tâche '" + taskName + "' terminée en " + duration.toMillis() + " ms.");
                recordReport(new WatchdogReport(taskName, now, duration, WatchdogReport.Status.COMPLETED, Optional.empty()));
                result.complete(value);
                return;
            }
            Throwable cause = unwrap(throwable);
            logger.warn("[Watchdog] Tâche '" + taskName + "' a échoué après " + duration.toMillis() + " ms.", cause);
            recordReport(new WatchdogReport(taskName, now, duration, WatchdogReport.Status.FAILED, Optional.of(cause)));
            result.completeExceptionally(cause);
        });
        return result;
    }

    @Override
    public void registerFallback(String taskName, Consumer<Throwable> fallbackAction) {
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(fallbackAction, "fallbackAction");
        fallbacks.put(taskName, fallbackAction);
    }

    @Override
    public WatchdogStatistics statistics() {
        long tasks = monitored.sum();
        long timeoutCount = timedOut.sum();
        double average = tasks > 0L ? (double) totalDurationMillis.sum() / tasks : 0.0D;
        return new WatchdogStatistics(tasks, timeoutCount, average);
    }

    @Override
    public List<WatchdogReport> recentReports() {
        synchronized (reportsLock) {
            if (reports.isEmpty()) {
                return List.of();
            }
            List<WatchdogReport> snapshot = new ArrayList<>(reports);
            Collections.reverse(snapshot);
            return List.copyOf(snapshot);
        }
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (stopped.compareAndSet(false, true)) {
            scheduler.shutdownNow();
            fallbacks.clear();
        }
        return WatchdogService.super.stop();
    }

    private void executeFallback(String taskName, Throwable cause) {
        Consumer<Throwable> fallback = fallbacks.remove(taskName);
        if (fallback == null) {
            return;
        }
        logger.warn("[Watchdog] Exécution du fallback pour la tâche '" + taskName + "' suite à un timeout.");
        executorManager.compute().execute(() -> {
            try {
                fallback.accept(cause);
            } catch (Throwable throwable) {
                logger.error("[Watchdog] Le fallback pour la tâche '" + taskName + "' a échoué.", throwable);
            }
        });
    }

    private void recordFailure(String taskName, Instant start, Throwable throwable) {
        Instant now = Instant.now();
        Duration duration = Duration.between(start, now);
        totalDurationMillis.add(Math.max(0L, duration.toMillis()));
        recordReport(new WatchdogReport(taskName, now, duration, WatchdogReport.Status.FAILED, Optional.ofNullable(throwable)));
        logger.warn("[Watchdog] Tâche '" + taskName + "' a échoué lors de l'initialisation.", throwable);
    }

    private void recordReport(WatchdogReport report) {
        synchronized (reportsLock) {
            if (reports.size() >= MAX_REPORTS) {
                reports.removeFirst();
            }
            reports.addLast(report);
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
