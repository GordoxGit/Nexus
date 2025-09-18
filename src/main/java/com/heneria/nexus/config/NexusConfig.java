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
    private final PerfSettings perfSettings;
    private final ThreadSettings threadSettings;
    private final DatabaseSettings databaseSettings;

    public NexusConfig(String serverMode,
                       Locale language,
                       ZoneId timezone,
                       PerfSettings perfSettings,
                       ThreadSettings threadSettings,
                       DatabaseSettings databaseSettings) {
        this.serverMode = Objects.requireNonNull(serverMode, "serverMode");
        this.language = Objects.requireNonNull(language, "language");
        this.timezone = Objects.requireNonNull(timezone, "timezone");
        this.perfSettings = Objects.requireNonNull(perfSettings, "perfSettings");
        this.threadSettings = Objects.requireNonNull(threadSettings, "threadSettings");
        this.databaseSettings = Objects.requireNonNull(databaseSettings, "databaseSettings");
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

    public PerfSettings perfSettings() {
        return perfSettings;
    }

    public ThreadSettings threadSettings() {
        return threadSettings;
    }

    public DatabaseSettings databaseSettings() {
        return databaseSettings;
    }

    public record PerfSettings(int hudHz, int scoreboardHz, int particlesSoftCap, int particlesHardCap) {
        public PerfSettings {
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
}
