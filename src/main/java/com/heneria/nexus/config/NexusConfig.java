package com.heneria.nexus.config;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable view over the main nexus configuration.
 */
public final class NexusConfig {

    private final String serverMode;
    private final Locale language;
    private final ZoneId timezone;
    private final ArenaSettings arenaSettings;
    private final ThreadSettings threadSettings;
    private final DatabaseSettings databaseSettings;
    private final ServiceSettings serviceSettings;
    private final TimeoutSettings timeoutSettings;
    private final DegradedModeSettings degradedModeSettings;
    private final QueueSettings queueSettings;

    public NexusConfig(String serverMode,
                       Locale language,
                       ZoneId timezone,
                       ArenaSettings arenaSettings,
                       ThreadSettings threadSettings,
                       DatabaseSettings databaseSettings,
                       ServiceSettings serviceSettings,
                       TimeoutSettings timeoutSettings,
                       DegradedModeSettings degradedModeSettings,
                       QueueSettings queueSettings) {
        this.serverMode = Objects.requireNonNull(serverMode, "serverMode");
        this.language = Objects.requireNonNull(language, "language");
        this.timezone = Objects.requireNonNull(timezone, "timezone");
        this.arenaSettings = Objects.requireNonNull(arenaSettings, "arenaSettings");
        this.threadSettings = Objects.requireNonNull(threadSettings, "threadSettings");
        this.databaseSettings = Objects.requireNonNull(databaseSettings, "databaseSettings");
        this.serviceSettings = Objects.requireNonNull(serviceSettings, "serviceSettings");
        this.timeoutSettings = Objects.requireNonNull(timeoutSettings, "timeoutSettings");
        this.degradedModeSettings = Objects.requireNonNull(degradedModeSettings, "degradedModeSettings");
        this.queueSettings = Objects.requireNonNull(queueSettings, "queueSettings");
    }

    public String serverMode() {
        return serverMode;
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

    public ThreadSettings threadSettings() {
        return threadSettings;
    }

    public DatabaseSettings databaseSettings() {
        return databaseSettings;
    }

    public ServiceSettings serviceSettings() {
        return serviceSettings;
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

    public record ArenaSettings(int hudHz, int scoreboardHz, int particlesSoftCap, int particlesHardCap,
                                int maxEntities, int maxItems, int maxProjectiles) {
        public ArenaSettings {
            if (hudHz <= 0) {
                throw new IllegalArgumentException("hudHz must be positive");
            }
            if (scoreboardHz <= 0) {
                throw new IllegalArgumentException("scoreboardHz must be positive");
            }
            if (particlesSoftCap < 0) {
                throw new IllegalArgumentException("particlesSoftCap must be >= 0");
            }
            if (particlesHardCap < particlesSoftCap) {
                throw new IllegalArgumentException("particlesHardCap must be >= particlesSoftCap");
            }
            if (maxEntities < 0 || maxItems < 0 || maxProjectiles < 0) {
                throw new IllegalArgumentException("budgets must be >= 0");
            }
        }
    }

    public record ThreadSettings(int ioPool, int computePool) {
        public ThreadSettings {
            if (ioPool <= 0) {
                throw new IllegalArgumentException("ioPool must be positive");
            }
            if (computePool <= 0) {
                throw new IllegalArgumentException("computePool must be positive");
            }
        }
    }

    public record DatabaseSettings(boolean enabled, String jdbcUrl, String username, String password, PoolSettings poolSettings) {
        public DatabaseSettings {
            Objects.requireNonNull(jdbcUrl, "jdbcUrl");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
            Objects.requireNonNull(poolSettings, "poolSettings");
        }
    }

    public record PoolSettings(int maxSize, int minIdle, long connectionTimeoutMs) {
        public PoolSettings {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("maxSize must be positive");
            }
            if (minIdle < 0) {
                throw new IllegalArgumentException("minIdle must be >= 0");
            }
            if (connectionTimeoutMs <= 0) {
                throw new IllegalArgumentException("connectionTimeoutMs must be positive");
            }
        }
    }

    public record ServiceSettings(boolean exposeBukkitServices) {
    }

    public record TimeoutSettings(long startMs, long stopMs) {
        public TimeoutSettings {
            if (startMs <= 0L) {
                throw new IllegalArgumentException("startMs must be positive");
            }
            if (stopMs <= 0L) {
                throw new IllegalArgumentException("stopMs must be positive");
            }
        }
    }

    public record DegradedModeSettings(boolean enabled, boolean banner) {
    }

    public record QueueSettings(int tickHz, int vipWeight) {
        public QueueSettings {
            if (tickHz <= 0) {
                throw new IllegalArgumentException("tickHz must be > 0");
            }
            if (vipWeight < 0) {
                throw new IllegalArgumentException("vipWeight must be >= 0");
            }
        }
    }
}
