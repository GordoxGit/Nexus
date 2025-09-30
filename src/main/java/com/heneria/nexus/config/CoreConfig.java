package com.heneria.nexus.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Particle;

/**
 * Immutable view over the main nexus configuration.
 */
public final class CoreConfig {

    private final String serverMode;
    private final String serverId;
    private final Locale language;
    private final ZoneId timezone;
    private final ArenaSettings arenaSettings;
    private final ExecutorSettings executorSettings;
    private final DatabaseSettings databaseSettings;
    private final RedisSettings redisSettings;
    private final RateLimitSettings rateLimitSettings;
    private final ServiceSettings serviceSettings;
    private final SecuritySettings securitySettings;
    private final BackupSettings backupSettings;
    private final TimeoutSettings timeoutSettings;
    private final DegradedModeSettings degradedModeSettings;
    private final QueueSettings queueSettings;
    private final HologramSettings hologramSettings;
    private final AnalyticsSettings analyticsSettings;
    private final UiSettings uiSettings;
    private final HealthCheckSettings healthCheckSettings;

    public CoreConfig(String serverMode,
                      String serverId,
                      Locale language,
                      ZoneId timezone,
                      ArenaSettings arenaSettings,
                      ExecutorSettings executorSettings,
                      DatabaseSettings databaseSettings,
                      RedisSettings redisSettings,
                      RateLimitSettings rateLimitSettings,
                      ServiceSettings serviceSettings,
                      SecuritySettings securitySettings,
                      BackupSettings backupSettings,
                      TimeoutSettings timeoutSettings,
                      DegradedModeSettings degradedModeSettings,
                      QueueSettings queueSettings,
                      HologramSettings hologramSettings,
                      AnalyticsSettings analyticsSettings,
                      UiSettings uiSettings,
                      HealthCheckSettings healthCheckSettings) {
        this.serverMode = Objects.requireNonNull(serverMode, "serverMode");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.language = Objects.requireNonNull(language, "language");
        this.timezone = Objects.requireNonNull(timezone, "timezone");
        this.arenaSettings = Objects.requireNonNull(arenaSettings, "arenaSettings");
        this.executorSettings = Objects.requireNonNull(executorSettings, "executorSettings");
        this.databaseSettings = Objects.requireNonNull(databaseSettings, "databaseSettings");
        this.redisSettings = Objects.requireNonNull(redisSettings, "redisSettings");
        this.rateLimitSettings = Objects.requireNonNull(rateLimitSettings, "rateLimitSettings");
        this.serviceSettings = Objects.requireNonNull(serviceSettings, "serviceSettings");
        this.securitySettings = Objects.requireNonNull(securitySettings, "securitySettings");
        this.backupSettings = Objects.requireNonNull(backupSettings, "backupSettings");
        this.timeoutSettings = Objects.requireNonNull(timeoutSettings, "timeoutSettings");
        this.degradedModeSettings = Objects.requireNonNull(degradedModeSettings, "degradedModeSettings");
        this.queueSettings = Objects.requireNonNull(queueSettings, "queueSettings");
        this.hologramSettings = Objects.requireNonNull(hologramSettings, "hologramSettings");
        this.analyticsSettings = Objects.requireNonNull(analyticsSettings, "analyticsSettings");
        this.uiSettings = Objects.requireNonNull(uiSettings, "uiSettings");
        this.healthCheckSettings = Objects.requireNonNull(healthCheckSettings, "healthCheckSettings");
    }

    public String serverMode() {
        return serverMode;
    }

    public String serverId() {
        return serverId;
    }

    public Locale language() {
        return language;
    }

    public ZoneId timezone() {
        return timezone;
    }

    public ArenaSettings arenaSettings() {
        return arenaSettings;
    }

    public ExecutorSettings executorSettings() {
        return executorSettings;
    }

    public DatabaseSettings databaseSettings() {
        return databaseSettings;
    }

    public RedisSettings redisSettings() {
        return redisSettings;
    }

    public RateLimitSettings rateLimitSettings() {
        return rateLimitSettings;
    }

    public ServiceSettings serviceSettings() {
        return serviceSettings;
    }

    public SecuritySettings securitySettings() {
        return securitySettings;
    }

    public BackupSettings backupSettings() {
        return backupSettings;
    }

    public TimeoutSettings timeoutSettings() {
        return timeoutSettings;
    }

    public DegradedModeSettings degradedModeSettings() {
        return degradedModeSettings;
    }

    public QueueSettings queueSettings() {
        return queueSettings;
    }

    public HologramSettings hologramSettings() {
        return hologramSettings;
    }

    public AnalyticsSettings analyticsSettings() {
        return analyticsSettings;
    }

    public UiSettings uiSettings() {
        return uiSettings;
    }

    public HealthCheckSettings healthCheckSettings() {
        return healthCheckSettings;
    }

    public record BackupSettings(int maxBackupsPerFile) {
        public BackupSettings {
            if (maxBackupsPerFile < 0) {
                throw new IllegalArgumentException("maxBackupsPerFile must be >= 0");
            }
        }
    }

    public record ArenaSettings(int hudHz,
                                int scoreboardHz,
                                int particlesSoftCap,
                                int particlesHardCap,
                                int maxEntities,
                                int maxItems,
                                int maxProjectiles,
                                SpawnProtectionSettings spawnProtection) {
        public ArenaSettings {
            if (hudHz <= 0) throw new IllegalArgumentException("hudHz must be positive");
            if (scoreboardHz <= 0) throw new IllegalArgumentException("scoreboardHz must be positive");
            if (particlesSoftCap < 0) throw new IllegalArgumentException("particlesSoftCap must be >= 0");
            if (particlesHardCap < particlesSoftCap) throw new IllegalArgumentException("particlesHardCap must be >= particlesSoftCap");
            if (maxEntities < 0 || maxItems < 0 || maxProjectiles < 0) throw new IllegalArgumentException("budgets must be >= 0");
            Objects.requireNonNull(spawnProtection, "spawnProtection");
        }

        public record SpawnProtectionSettings(boolean enabled,
                                              Duration duration,
                                              int resistanceAmplifier,
                                              boolean glow,
                                              Particle particle,
                                              String actionBarMessage,
                                              int actionBarIntervalTicks) {

            public SpawnProtectionSettings {
                Objects.requireNonNull(duration, "duration");
                if (duration.isNegative() || duration.isZero()) {
                    throw new IllegalArgumentException("duration must be > 0");
                }
                if (resistanceAmplifier < 0) {
                    throw new IllegalArgumentException("resistanceAmplifier must be >= 0");
                }
                Objects.requireNonNull(actionBarMessage, "actionBarMessage");
                if (actionBarIntervalTicks <= 0) {
                    throw new IllegalArgumentException("actionBarIntervalTicks must be > 0");
                }
            }
        }
    }

    public record ExecutorSettings(IoSettings io, ComputeSettings compute, ShutdownSettings shutdown, SchedulerSettings scheduler) {
        public ExecutorSettings {
            Objects.requireNonNull(io, "io");
            Objects.requireNonNull(compute, "compute");
            Objects.requireNonNull(shutdown, "shutdown");
            Objects.requireNonNull(scheduler, "scheduler");
        }
        public record IoSettings(boolean virtual, int maxThreads, long keepAliveMs) {
            public IoSettings {
                if (maxThreads <= 0) throw new IllegalArgumentException("maxThreads must be positive");
                if (keepAliveMs < 0L) throw new IllegalArgumentException("keepAliveMs must be >= 0");
            }
        }
        public record ComputeSettings(int size) {
            public ComputeSettings {
                if (size <= 0) throw new IllegalArgumentException("size must be positive");
            }
        }
        public record ShutdownSettings(long awaitSeconds, long forceCancelSeconds) {
            public ShutdownSettings {
                if (awaitSeconds < 0L) throw new IllegalArgumentException("awaitSeconds must be >= 0");
                if (forceCancelSeconds < 0L) throw new IllegalArgumentException("forceCancelSeconds must be >= 0");
            }
        }
        public record SchedulerSettings(int mainCheckIntervalTicks) {
            public SchedulerSettings {
                if (mainCheckIntervalTicks <= 0) throw new IllegalArgumentException("mainCheckIntervalTicks must be > 0");
            }
        }
    }

    public record DatabaseSettings(boolean enabled, String jdbcUrl, String username, String password,
                                   PoolSettings poolSettings, Duration writeBehindInterval,
                                   CacheSettings cacheSettings, MonitoringSettings monitoring,
                                   DataRetentionSettings retentionPolicy, ResilienceSettings resilience) {
        public DatabaseSettings {
            Objects.requireNonNull(jdbcUrl, "jdbcUrl");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
            Objects.requireNonNull(poolSettings, "poolSettings");
            Objects.requireNonNull(writeBehindInterval, "writeBehindInterval");
            Objects.requireNonNull(cacheSettings, "cacheSettings");
            Objects.requireNonNull(monitoring, "monitoring");
            Objects.requireNonNull(retentionPolicy, "retentionPolicy");
            Objects.requireNonNull(resilience, "resilience");
            if (writeBehindInterval.isZero() || writeBehindInterval.isNegative()) {
                throw new IllegalArgumentException("writeBehindInterval must be > 0");
            }
        }

        public record CacheSettings(ProfileCacheSettings profiles) {
            public CacheSettings {
                Objects.requireNonNull(profiles, "profiles");
            }
        }

        public record MonitoringSettings(boolean enableSqlTracing, long slowQueryThresholdMs) {
            public MonitoringSettings {
                if (slowQueryThresholdMs < 0L) {
                    throw new IllegalArgumentException("slowQueryThresholdMs must be >= 0");
                }
            }
        }

        public record ProfileCacheSettings(long maxSize, Duration expireAfterAccess) {
            public ProfileCacheSettings {
                if (maxSize <= 0L) {
                    throw new IllegalArgumentException("maxSize must be > 0");
                }
                Objects.requireNonNull(expireAfterAccess, "expireAfterAccess");
                if (expireAfterAccess.isZero() || expireAfterAccess.isNegative()) {
                    throw new IllegalArgumentException("expireAfterAccess must be > 0");
                }
            }
        }

        public record DataRetentionSettings(int matchHistoryDays) {
            public DataRetentionSettings {
                if (matchHistoryDays < 0) {
                    throw new IllegalArgumentException("matchHistoryDays must be >= 0");
                }
            }
        }

        public record ResilienceSettings(RetrySettings retry, CircuitBreakerSettings circuitBreaker) {
            public ResilienceSettings {
                Objects.requireNonNull(retry, "retry");
                Objects.requireNonNull(circuitBreaker, "circuitBreaker");
            }

            public record RetrySettings(int maxAttempts, Duration initialInterval, Duration maxInterval, double multiplier) {
                public RetrySettings {
                    Objects.requireNonNull(initialInterval, "initialInterval");
                    Objects.requireNonNull(maxInterval, "maxInterval");
                    if (maxAttempts <= 0) {
                        throw new IllegalArgumentException("maxAttempts must be > 0");
                    }
                    if (initialInterval.isZero() || initialInterval.isNegative()) {
                        throw new IllegalArgumentException("initialInterval must be > 0");
                    }
                    if (maxInterval.isZero() || maxInterval.isNegative()) {
                        throw new IllegalArgumentException("maxInterval must be > 0");
                    }
                    if (maxInterval.compareTo(initialInterval) < 0) {
                        throw new IllegalArgumentException("maxInterval must be >= initialInterval");
                    }
                    if (multiplier < 1D) {
                        throw new IllegalArgumentException("multiplier must be >= 1");
                    }
                }
            }

            public record CircuitBreakerSettings(double failureRateThreshold,
                                                 int minimumNumberOfCalls,
                                                 Duration slidingWindowDuration,
                                                 Duration waitDurationInOpenState,
                                                 int permittedCallsInHalfOpenState) {
                public CircuitBreakerSettings {
                    Objects.requireNonNull(slidingWindowDuration, "slidingWindowDuration");
                    Objects.requireNonNull(waitDurationInOpenState, "waitDurationInOpenState");
                    if (failureRateThreshold <= 0D || failureRateThreshold > 100D) {
                        throw new IllegalArgumentException("failureRateThreshold must be in (0, 100]");
                    }
                    if (minimumNumberOfCalls <= 0) {
                        throw new IllegalArgumentException("minimumNumberOfCalls must be > 0");
                    }
                    if (slidingWindowDuration.isZero() || slidingWindowDuration.isNegative()) {
                        throw new IllegalArgumentException("slidingWindowDuration must be > 0");
                    }
                    if (waitDurationInOpenState.isZero() || waitDurationInOpenState.isNegative()) {
                        throw new IllegalArgumentException("waitDurationInOpenState must be > 0");
                    }
                    if (permittedCallsInHalfOpenState <= 0) {
                        throw new IllegalArgumentException("permittedCallsInHalfOpenState must be > 0");
                    }
                }
            }
        }
    }

    public record RedisSettings(boolean enabled, String host, int port, String password, long timeoutMs) {
        public RedisSettings {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(password, "password");
            if (port <= 0 || port > 65_535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
            if (timeoutMs <= 0L) {
                throw new IllegalArgumentException("timeoutMs must be > 0");
            }
        }
    }

    public record RateLimitSettings(boolean enabled,
                                    Map<String, Duration> cooldowns,
                                    Duration cleanupInterval,
                                    Duration retentionDuration) {
        public RateLimitSettings {
            Objects.requireNonNull(cooldowns, "cooldowns");
            Objects.requireNonNull(cleanupInterval, "cleanupInterval");
            Objects.requireNonNull(retentionDuration, "retentionDuration");
            if (cleanupInterval.isZero() || cleanupInterval.isNegative()) {
                throw new IllegalArgumentException("cleanupInterval must be > 0");
            }
            if (retentionDuration.isZero() || retentionDuration.isNegative()) {
                throw new IllegalArgumentException("retentionDuration must be > 0");
            }
            Map<String, Duration> validated = new LinkedHashMap<>();
            for (Map.Entry<String, Duration> entry : cooldowns.entrySet()) {
                Duration value = Objects.requireNonNull(entry.getValue(),
                        "cooldown for action '%s' cannot be null".formatted(entry.getKey()));
                if (value.isNegative()) {
                    throw new IllegalArgumentException(
                            "cooldown for action '%s' must be >= 0".formatted(entry.getKey()));
                }
                validated.put(entry.getKey(), value);
            }
            cooldowns = Map.copyOf(validated);
        }

        public Duration cooldownFor(String actionKey) {
            return cooldowns.getOrDefault(actionKey, Duration.ZERO);
        }

        public Duration maxConfiguredCooldown() {
            Duration max = Duration.ZERO;
            for (Duration value : cooldowns.values()) {
                if (value.compareTo(max) > 0) {
                    max = value;
                }
            }
            return max;
        }
    }

    public record PoolSettings(int maxSize, int minIdle, long connectionTimeoutMs) {
        public PoolSettings {
            if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
            if (minIdle < 0) throw new IllegalArgumentException("minIdle must be >= 0");
            if (connectionTimeoutMs <= 0) throw new IllegalArgumentException("connectionTimeoutMs must be positive");
        }
    }

    public record ServiceSettings(boolean exposeBukkitServices) {}

    public record SecuritySettings(Set<String> allowedChannels,
                                   NetworkRateLimitSettings networkRateLimitSettings) {

        public SecuritySettings {
            Objects.requireNonNull(allowedChannels, "allowedChannels");
            Objects.requireNonNull(networkRateLimitSettings, "networkRateLimitSettings");
            Set<String> normalized = allowedChannels.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(channel -> !channel.isEmpty())
                    .map(channel -> channel.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            this.allowedChannels = Collections.unmodifiableSet(normalized);
            this.networkRateLimitSettings = networkRateLimitSettings;
        }

        public record NetworkRateLimitSettings(boolean enabled, boolean failOpen, Map<String, Integer> limits) {

            public NetworkRateLimitSettings {
                Objects.requireNonNull(limits, "limits");
                Map<String, Integer> sanitized = new LinkedHashMap<>();
                limits.forEach((channel, limit) -> {
                    if (channel == null) {
                        return;
                    }
                    String normalized = channel.trim();
                    if (normalized.isEmpty()) {
                        return;
                    }
                    if (limit == null || limit <= 0) {
                        return;
                    }
                    sanitized.put(normalized.toLowerCase(Locale.ROOT), limit);
                });
                this.limits = Collections.unmodifiableMap(sanitized);
            }
        }
    }

    public record HologramSettings(int updateHz, int maxVisiblePerInstance, double lineSpacing, double viewRange, int maxPooledTextDisplays, int maxPooledInteractions) {
        public HologramSettings {
            if (updateHz <= 0) throw new IllegalArgumentException("updateHz must be positive");
            if (maxVisiblePerInstance <= 0) throw new IllegalArgumentException("maxVisiblePerInstance must be positive");
            if (lineSpacing <= 0D) throw new IllegalArgumentException("lineSpacing must be > 0");
            if (viewRange <= 0D) throw new IllegalArgumentException("viewRange must be > 0");
            if (maxPooledTextDisplays < 0) throw new IllegalArgumentException("maxPooledTextDisplays must be >= 0");
            if (maxPooledInteractions < 0) throw new IllegalArgumentException("maxPooledInteractions must be >= 0");
        }
    }

    public record AnalyticsSettings(OutboxSettings outbox) {
        public AnalyticsSettings {
            Objects.requireNonNull(outbox, "outbox");
        }

        public record OutboxSettings(boolean enabled, Duration flushInterval, int maxBatchSize) {
            public OutboxSettings {
                Objects.requireNonNull(flushInterval, "flushInterval");
                if (flushInterval.isZero() || flushInterval.isNegative()) {
                    throw new IllegalArgumentException("flushInterval must be > 0");
                }
                if (maxBatchSize <= 0) {
                    throw new IllegalArgumentException("maxBatchSize must be > 0");
                }
            }
        }
    }

    public record TimeoutSettings(long startMs, long stopMs, WatchdogSettings watchdog) {
        public TimeoutSettings {
            if (startMs <= 0L) throw new IllegalArgumentException("startMs must be positive");
            if (stopMs <= 0L) throw new IllegalArgumentException("stopMs must be positive");
            Objects.requireNonNull(watchdog, "watchdog");
        }
        public record WatchdogSettings(long resetMs, long pasteMs) {
            public WatchdogSettings {
                if (resetMs <= 0L) throw new IllegalArgumentException("resetMs must be positive");
                if (pasteMs <= 0L) throw new IllegalArgumentException("pasteMs must be positive");
            }
        }
    }

    public record DegradedModeSettings(boolean enabled, boolean banner) {}

    public record QueueSettings(int tickHz,
                                int vipWeight,
                                String targetServerId,
                                String hubGroup,
                                CrossShardSettings crossShard) {
        public QueueSettings {
            if (tickHz <= 0) throw new IllegalArgumentException("tickHz must be > 0");
            if (vipWeight < 0) throw new IllegalArgumentException("vipWeight must be >= 0");
            targetServerId = Objects.requireNonNull(targetServerId, "targetServerId").trim();
            hubGroup = Objects.requireNonNull(hubGroup, "hubGroup").trim();
            if (targetServerId.isEmpty()) {
                throw new IllegalArgumentException("targetServerId must not be empty");
            }
            if (hubGroup.isEmpty()) {
                throw new IllegalArgumentException("hubGroup must not be empty");
            }
            crossShard = Objects.requireNonNull(crossShard, "crossShard");
        }

        public record CrossShardSettings(boolean enabled, String redisKeyPrefix, long lockTtlMs) {
            public CrossShardSettings {
                redisKeyPrefix = Objects.requireNonNull(redisKeyPrefix, "redisKeyPrefix").trim();
                if (redisKeyPrefix.isEmpty()) {
                    throw new IllegalArgumentException("redisKeyPrefix must not be empty");
                }
                if (lockTtlMs <= 0L) {
                    throw new IllegalArgumentException("lockTtlMs must be > 0");
                }
            }
        }
    }

    public record UiSettings(boolean strictMiniMessage, Map<String, TitleTimesProfile> titleProfiles, BossBarDefaults bossBarDefaults) {
        public UiSettings {
            Objects.requireNonNull(titleProfiles, "titleProfiles");
            Objects.requireNonNull(bossBarDefaults, "bossBarDefaults");
        }
    }

    public record HealthCheckSettings(boolean enabled, long intervalSeconds) {
        public HealthCheckSettings {
            if (intervalSeconds <= 0L) {
                throw new IllegalArgumentException("intervalSeconds must be > 0");
            }
        }
    }

    public record TitleTimesProfile(int fadeInTicks, int stayTicks, int fadeOutTicks) {
        public TitleTimesProfile {
            if (fadeInTicks < 0) throw new IllegalArgumentException("fadeInTicks must be >= 0");
            if (stayTicks <= 0) throw new IllegalArgumentException("stayTicks must be > 0");
            if (fadeOutTicks < 0) throw new IllegalArgumentException("fadeOutTicks must be >= 0");
        }
    }

    public record BossBarDefaults(BossBar.Color color, BossBar.Overlay overlay, Set<BossBar.Flag> flags, int updateEveryTicks) {
        public BossBarDefaults {
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(overlay, "overlay");
            Objects.requireNonNull(flags, "flags");
            if (updateEveryTicks <= 0) throw new IllegalArgumentException("updateEveryTicks must be > 0");
        }
    }
}
