package com.heneria.nexus.service.core;

import com.heneria.nexus.config.NexusConfig;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.service.ExecutorPools;
import com.heneria.nexus.service.api.PlayerProfile;
import com.heneria.nexus.service.api.ProfileService;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default profile service relying on the database provider with in-memory fallback.
 */
public final class ProfileServiceImpl implements ProfileService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final NexusLogger logger;
    private final DbProvider dbProvider;
    private final ExecutorService ioExecutor;
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerProfile> persistentStore = new ConcurrentHashMap<>();
    private final AtomicBoolean degraded = new AtomicBoolean();
    private final AtomicReference<NexusConfig.DegradedModeSettings> degradedSettings = new AtomicReference<>();

    public ProfileServiceImpl(NexusLogger logger, DbProvider dbProvider, ExecutorPools executorPools, NexusConfig config) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.ioExecutor = Objects.requireNonNull(executorPools, "executorPools").ioExecutor();
        this.degradedSettings.set(config.degradedModeSettings());
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(this::refreshDegradedState, ioExecutor);
    }

    @Override
    public CompletableFuture<PlayerProfile> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CacheEntry cached = cache.get(playerId);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(copyProfile(cached.profile()));
        }
        return CompletableFuture.supplyAsync(() -> {
            boolean fallback = refreshDegradedState();
            if (fallback) {
                return cache.compute(playerId, (id, entry) -> {
                    PlayerProfile profile = entry != null ? entry.profile() : defaultProfile(playerId);
                    return new CacheEntry(copyProfile(profile), Instant.now());
                }).profile();
            }
            PlayerProfile stored = persistentStore.computeIfAbsent(playerId, this::defaultProfile);
            CacheEntry entry = new CacheEntry(copyProfile(stored), Instant.now());
            cache.put(playerId, entry);
            return copyProfile(entry.profile());
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> saveAsync(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        PlayerProfile copy = copyProfile(profile);
        return CompletableFuture.runAsync(() -> {
            persistentStore.put(profile.playerId(), copyProfile(copy));
            cache.put(profile.playerId(), new CacheEntry(copyProfile(copy), Instant.now()));
        }, ioExecutor);
    }

    @Override
    public void invalidate(UUID playerId) {
        cache.remove(playerId);
    }

    @Override
    public void applyDegradedModeSettings(NexusConfig.DegradedModeSettings settings) {
        degradedSettings.set(Objects.requireNonNull(settings, "settings"));
    }

    @Override
    public boolean isDegraded() {
        return degraded.get();
    }

    private boolean refreshDegradedState() {
        boolean fallback = dbProvider.isDegraded();
        boolean previous = degraded.getAndSet(fallback);
        if (fallback && !previous) {
            NexusConfig.DegradedModeSettings settings = degradedSettings.get();
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
        return new PlayerProfile(playerId, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), List.of(), Instant.now());
    }

    private PlayerProfile copyProfile(PlayerProfile profile) {
        return new PlayerProfile(
                profile.playerId(),
                new ConcurrentHashMap<>(profile.statistics()),
                new ConcurrentHashMap<>(profile.preferences()),
                List.copyOf(profile.cosmetics()),
                profile.lastUpdate());
    }

    private record CacheEntry(PlayerProfile profile, Instant loadedAt) {
        boolean isExpired() {
            return loadedAt.plus(CACHE_TTL).isBefore(Instant.now());
        }
    }
}
