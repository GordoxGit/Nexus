package com.heneria.nexus.service.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.heneria.nexus.api.PlayerProfile;
import com.heneria.nexus.api.ProfileService;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.db.OptimisticLockException;
import com.heneria.nexus.db.repository.ProfileRepository;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default profile service relying on the database provider with in-memory fallback.
 */
public final class ProfileServiceImpl implements ProfileService {

    private static final String STAT_ELO = "elo_rating";
    private static final String STAT_TOTAL_KILLS = "total_kills";
    private static final String STAT_TOTAL_DEATHS = "total_deaths";
    private static final String STAT_TOTAL_WINS = "total_wins";
    private static final String STAT_TOTAL_LOSSES = "total_losses";
    private static final String STAT_MATCHES_PLAYED = "matches_played";

    private final NexusLogger logger;
    private final DbProvider dbProvider;
    private final ExecutorManager executorManager;
    private final ProfileRepository profileRepository;
    private final PersistenceService persistenceService;
    private final Cache<UUID, PlayerProfile> profileCache;
    private final ConcurrentHashMap<UUID, PlayerProfile> persistentStore = new ConcurrentHashMap<>();
    private final AtomicBoolean degraded = new AtomicBoolean();
    private final AtomicBoolean forcedFallback = new AtomicBoolean();
    private final AtomicReference<CoreConfig.DegradedModeSettings> degradedSettings = new AtomicReference<>();

    public ProfileServiceImpl(NexusLogger logger,
                              DbProvider dbProvider,
                              ExecutorManager executorManager,
                              CoreConfig config,
                              ProfileRepository profileRepository,
                              PersistenceService persistenceService) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.profileCache = createCache(config);
        this.degradedSettings.set(config.degradedModeSettings());
    }

    private Cache<UUID, PlayerProfile> createCache(CoreConfig config) {
        Objects.requireNonNull(config, "config");
        long maxSize = 1000L;
        Duration expireAfterAccess = Duration.ofMinutes(15L);
        CoreConfig.DatabaseSettings databaseSettings = config.databaseSettings();
        if (databaseSettings != null) {
            CoreConfig.DatabaseSettings.CacheSettings cacheSettings = databaseSettings.cacheSettings();
            if (cacheSettings != null && cacheSettings.profiles() != null) {
                CoreConfig.DatabaseSettings.ProfileCacheSettings profileSettings = cacheSettings.profiles();
                maxSize = profileSettings.maxSize();
                expireAfterAccess = profileSettings.expireAfterAccess();
            }
        }
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expireAfterAccess)
                .build();
    }

    @Override
    public CompletableFuture<Void> start() {
        return executorManager.runIo(() -> refreshDegradedState());
    }

    @Override
    public CompletableFuture<PlayerProfile> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerProfile cached = profileCache.getIfPresent(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(copyProfile(cached));
        }
        boolean providerDegraded = dbProvider.isDegraded();
        refreshDegradedState(providerDegraded);
        if (providerDegraded) {
            return executorManager.supplyIo(() -> loadFromFallback(playerId));
        }
        if (forcedFallback.get()) {
            triggerHealthProbe();
            return executorManager.supplyIo(() -> loadFromFallback(playerId));
        }
        return profileRepository.findByUuid(playerId)
                .thenCompose(optional -> {
                    if (optional.isPresent()) {
                        return CompletableFuture.completedFuture(optional.get());
                    }
                    PlayerProfile created = defaultProfile(playerId);
                    snapshotLocally(created);
                    persistenceService.markProfileDirty(playerId, () -> persistenceSnapshot(playerId));
                    return CompletableFuture.completedFuture(created);
                })
                .thenApply(profile -> {
                    clearForcedFallback();
                    return snapshotAndCopy(profile);
                })
                .exceptionallyCompose(throwable -> fallbackLoad(playerId, throwable));
    }

    @Override
    public CompletableFuture<Void> saveAsync(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        profile.incrementVersion();
        PlayerProfile snapshot = copyProfile(profile);
        snapshotLocally(snapshot);

        boolean providerDegraded = dbProvider.isDegraded();
        refreshDegradedState(providerDegraded);
        if (providerDegraded) {
            persistenceService.markProfileDirty(profile.playerId(), () -> persistenceSnapshot(profile.playerId()));
            return CompletableFuture.completedFuture(null);
        }
        if (forcedFallback.get()) {
            triggerHealthProbe();
            persistenceService.markProfileDirty(profile.playerId(), () -> persistenceSnapshot(profile.playerId()));
            return CompletableFuture.completedFuture(null);
        }

        return profileRepository.createOrUpdate(snapshot)
                .thenRun(() -> {
                    snapshot.markPersisted();
                    clearForcedFallback();
                })
                .exceptionallyCompose(throwable -> {
                    Throwable cause = unwrap(throwable);
                    if (cause instanceof OptimisticLockException) {
                        logger.warn(
                                "Conflit de verrouillage optimiste pour %s, tentative de rechargement ultérieur"
                                        .formatted(profile.playerId()),
                                cause);
                        return profileRepository.findByUuid(profile.playerId())
                                .thenCompose(optional -> {
                                    optional.ifPresent(this::snapshotLocally);
                                    if (optional.isEmpty()) {
                                        persistentStore.remove(profile.playerId());
                                        profileCache.invalidate(profile.playerId());
                                    }
                                    return CompletableFuture.<Void>completedFuture(null);
                                })
                                .exceptionally(inner -> {
                                    logger.warn(
                                            "Impossible de recharger le profil %s après conflit"
                                                    .formatted(profile.playerId()),
                                            unwrap(inner));
                                    return null;
                                });
                    }
                    return fallbackSave(snapshot, throwable);
                });
    }

    @Override
    public void invalidate(UUID playerId) {
        profileCache.invalidate(playerId);
    }

    @Override
    public void applyDegradedModeSettings(CoreConfig.DegradedModeSettings settings) {
        degradedSettings.set(Objects.requireNonNull(settings, "settings"));
    }

    @Override
    public boolean isDegraded() {
        return degraded.get();
    }

    private boolean refreshDegradedState() {
        return refreshDegradedState(dbProvider.isDegraded());
    }

    private boolean refreshDegradedState(boolean providerDegraded) {
        boolean fallback = providerDegraded || forcedFallback.get();
        boolean previous = degraded.getAndSet(fallback);
        if (fallback && !previous) {
            CoreConfig.DegradedModeSettings settings = degradedSettings.get();
            if (settings.banner()) {
                logger.warn("Mode dégradé activé pour le ProfileService : stockage en mémoire");
            }
        }
        if (!fallback && previous) {
            logger.info("ProfileService repassé en mode persistant");
        }
        return fallback;
    }

    private PlayerProfile defaultProfile(UUID playerId) {
        ConcurrentHashMap<String, Long> statistics = new ConcurrentHashMap<>();
        statistics.put(STAT_ELO, 1000L);
        statistics.put(STAT_TOTAL_KILLS, 0L);
        statistics.put(STAT_TOTAL_DEATHS, 0L);
        statistics.put(STAT_TOTAL_WINS, 0L);
        statistics.put(STAT_TOTAL_LOSSES, 0L);
        statistics.put(STAT_MATCHES_PLAYED, 0L);
        return new PlayerProfile(playerId, statistics, new ConcurrentHashMap<>(), new ArrayList<>(), Instant.now(), 0);
    }

    private PlayerProfile copyProfile(PlayerProfile profile) {
        return new PlayerProfile(
                profile.playerId(),
                new ConcurrentHashMap<>(profile.statistics()),
                new ConcurrentHashMap<>(profile.preferences()),
                new ArrayList<>(profile.cosmetics()),
                profile.lastUpdate(),
                profile.getVersion(),
                profile.getPersistedVersion());
    }

    private PlayerProfile snapshotAndCopy(PlayerProfile profile) {
        snapshotLocally(profile);
        PlayerProfile cached = profileCache.getIfPresent(profile.playerId());
        return cached != null ? copyProfile(cached) : copyProfile(profile);
    }

    private PlayerProfile loadFromFallback(UUID playerId) {
        boolean[] created = new boolean[1];
        PlayerProfile stored = persistentStore.compute(playerId, (id, existing) -> {
            if (existing != null) {
                return existing;
            }
            created[0] = true;
            return defaultProfile(id);
        });
        PlayerProfile cached = copyProfile(stored);
        profileCache.put(playerId, cached);
        if (created[0]) {
            persistenceService.markProfileDirty(playerId, () -> persistenceSnapshot(playerId));
        }
        return copyProfile(cached);
    }

    private void snapshotLocally(PlayerProfile profile) {
        persistentStore.put(profile.playerId(), profile);
        profileCache.put(profile.playerId(), copyProfile(profile));
    }

    PlayerProfile persistenceSnapshot(UUID playerId) {
        PlayerProfile canonical = persistentStore.get(playerId);
        if (canonical != null) {
            return canonical;
        }
        PlayerProfile cached = profileCache.getIfPresent(playerId);
        if (cached == null) {
            return null;
        }
        PlayerProfile restored = copyProfile(cached);
        persistentStore.put(playerId, restored);
        return restored;
    }

    private CompletableFuture<PlayerProfile> fallbackLoad(UUID playerId, Throwable throwable) {
        activateFallback("chargement du profil", throwable);
        return executorManager.supplyIo(() -> loadFromFallback(playerId));
    }

    private CompletableFuture<Void> fallbackSave(PlayerProfile snapshot, Throwable throwable) {
        activateFallback("sauvegarde du profil", throwable);
        snapshotLocally(snapshot);
        persistenceService.markProfileDirty(snapshot.playerId(), () -> persistenceSnapshot(snapshot.playerId()));
        return CompletableFuture.completedFuture(null);
    }

    private void activateFallback(String action, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (forcedFallback.compareAndSet(false, true)) {
            logger.warn("Échec lors du %s, bascule en mode mémoire".formatted(action), cause);
        } else {
            logger.debug(() -> "Échec lors du %s : %s".formatted(action, cause != null ? cause.getMessage() : "inconnu"));
        }
        refreshDegradedState();
        triggerHealthProbe();
    }

    private void clearForcedFallback() {
        if (forcedFallback.compareAndSet(true, false)) {
            refreshDegradedState();
        }
    }

    private void triggerHealthProbe() {
        if (!forcedFallback.get()) {
            return;
        }
        dbProvider.checkHealth(executorManager.io())
                .thenAccept(healthy -> {
                    if (healthy && forcedFallback.compareAndSet(true, false)) {
                        refreshDegradedState();
                    }
                });
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completion && completion.getCause() != null) {
            return unwrap(completion.getCause());
        }
        if (throwable instanceof ExecutionException execution && execution.getCause() != null) {
            return unwrap(execution.getCause());
        }
        return throwable;
    }

}
