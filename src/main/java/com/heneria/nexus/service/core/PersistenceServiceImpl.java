package com.heneria.nexus.service.core;

import com.heneria.nexus.api.PlayerProfile;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.db.repository.EconomyRepository;
import com.heneria.nexus.db.repository.ProfileRepository;
import com.heneria.nexus.util.NamedThreadFactory;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Default implementation of the write-behind persistence orchestrator.
 */
public final class PersistenceServiceImpl implements PersistenceService {

    private final NexusLogger logger;
    private final ProfileRepository profileRepository;
    private final EconomyRepository economyRepository;
    private final Duration flushInterval;
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, DirtyEntry> dirtyEntries = new ConcurrentHashMap<>();
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledExecutorService scheduler;

    public PersistenceServiceImpl(NexusLogger logger,
                                  ExecutorManager executorManager,
                                  CoreConfig coreConfig,
                                  ProfileRepository profileRepository,
                                  EconomyRepository economyRepository) {
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(executorManager, "executorManager");
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository");
        this.economyRepository = Objects.requireNonNull(economyRepository, "economyRepository");
        Objects.requireNonNull(coreConfig, "coreConfig");
        this.flushInterval = coreConfig.databaseSettings().writeBehindInterval();
    }

    @Override
    public CompletableFuture<Void> start() {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("Nexus-Persistence", true, logger));
        long intervalMs = Math.max(1L, flushInterval.toMillis());
        executor.scheduleAtFixedRate(this::runScheduledFlush, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        this.scheduler = executor;
        logger.info("Write-behind persistence activée (intervalle=%d ms)".formatted(intervalMs));
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
        return flushWithLock(true);
    }

    @Override
    public void markProfileDirty(UUID playerId, Supplier<PlayerProfile> snapshotSupplier) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        dirtyEntries.compute(playerId, (id, existing) -> {
            DirtyEntry entry = existing != null ? existing : new DirtyEntry();
            entry.profileSupplier = snapshotSupplier;
            return entry;
        });
        dirtyPlayers.add(playerId);
    }

    @Override
    public void markEconomyDirty(UUID playerId, LongSupplier balanceSupplier) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(balanceSupplier, "balanceSupplier");
        dirtyEntries.compute(playerId, (id, existing) -> {
            DirtyEntry entry = existing != null ? existing : new DirtyEntry();
            entry.balanceSupplier = balanceSupplier;
            return entry;
        });
        dirtyPlayers.add(playerId);
    }

    @Override
    public void flushAllOnShutdown() {
        try {
            flushWithLock(true).join();
        } catch (Exception exception) {
            Throwable cause = exception instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null
                    ? ce.getCause()
                    : exception;
            logger.error("Échec lors du flush final du cache de persistance", cause);
        }
    }

    private void runScheduledFlush() {
        if (!running.get()) {
            return;
        }
        flushWithLock(false);
    }

    private CompletableFuture<Void> flushWithLock(boolean blocking) {
        boolean acquired;
        if (blocking) {
            flushLock.lock();
            acquired = true;
        } else {
            acquired = flushLock.tryLock();
        }
        if (!acquired) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future;
        try {
            future = flushInternal();
        } catch (Throwable throwable) {
            flushLock.unlock();
            throw throwable;
        }
        future.whenComplete((ignored, throwable) -> flushLock.unlock());
        return future;
    }

    private CompletableFuture<Void> flushInternal() {
        Map<UUID, DirtyEntry> snapshot = snapshotDirtyEntries();
        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        ArrayList<PlayerProfile> profiles = new ArrayList<>();
        HashMap<UUID, Long> balances = new HashMap<>();
        try {
            snapshot.forEach((uuid, entry) -> {
                Supplier<PlayerProfile> profileSupplier = entry.profileSupplier;
                if (profileSupplier != null) {
                    PlayerProfile profile = invokeProfileSupplier(uuid, profileSupplier);
                    if (profile != null) {
                        profiles.add(profile);
                    }
                }
                LongSupplier balanceSupplier = entry.balanceSupplier;
                if (balanceSupplier != null) {
                    long balance = invokeBalanceSupplier(uuid, balanceSupplier);
                    balances.put(uuid, balance);
                }
            });
        } catch (Throwable throwable) {
            snapshot.forEach((uuid, entry) -> {
                dirtyEntries.put(uuid, entry);
                dirtyPlayers.add(uuid);
            });
            throw throwable;
        }
        CompletableFuture<Void> persistFuture = persistBatch(profiles, balances);
        return persistFuture.whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                if (!profiles.isEmpty() || !balances.isEmpty()) {
                    logger.info("[NEXUS] Sauvegardé %d profils et %d soldes modifiés en base de données.".formatted(
                            profiles.size(), balances.size()));
                }
            } else {
                logger.error("Erreur lors de la sauvegarde batch des données joueurs", throwable);
                snapshot.forEach((uuid, entry) -> {
                    dirtyEntries.put(uuid, entry);
                    dirtyPlayers.add(uuid);
                });
            }
        });
    }

    private Map<UUID, DirtyEntry> snapshotDirtyEntries() {
        Map<UUID, DirtyEntry> snapshot = new HashMap<>();
        synchronized (dirtyPlayers) {
            if (dirtyPlayers.isEmpty()) {
                return snapshot;
            }
            for (UUID uuid : dirtyPlayers) {
                DirtyEntry entry = dirtyEntries.remove(uuid);
                if (entry != null) {
                    snapshot.put(uuid, entry);
                }
            }
            dirtyPlayers.clear();
        }
        return snapshot;
    }

    private PlayerProfile invokeProfileSupplier(UUID playerId, Supplier<PlayerProfile> supplier) {
        try {
            return supplier.get();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Impossible d'obtenir le profil pour " + playerId, throwable);
        }
    }

    private long invokeBalanceSupplier(UUID playerId, LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Impossible d'obtenir le solde pour " + playerId, throwable);
        }
    }

    private CompletableFuture<Void> persistBatch(ArrayList<PlayerProfile> profiles, HashMap<UUID, Long> balances) {
        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>(2);
        if (!profiles.isEmpty()) {
            futures.add(profileRepository.saveAll(profiles));
        }
        if (!balances.isEmpty()) {
            futures.add(economyRepository.saveAll(balances));
        }
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static final class DirtyEntry {
        private Supplier<PlayerProfile> profileSupplier;
        private LongSupplier balanceSupplier;
    }
}
