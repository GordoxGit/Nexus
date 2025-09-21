package com.heneria.nexus.config;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.bossbar.BossBar;

/**
 * Immutable view over the main nexus configuration.
 */
public final class CoreConfig {

    private final String serverMode;
    private final Locale language;
    private final ZoneId timezone;
    private final ArenaSettings arenaSettings;
    private final ExecutorSettings executorSettings;
    private final DatabaseSettings databaseSettings;
    private final ServiceSettings serviceSettings;
    private final TimeoutSettings timeoutSettings;
    private final DegradedModeSettings degradedModeSettings;
    private final QueueSettings queueSettings;
    private final HologramSettings hologramSettings;
    private final UiSettings uiSettings;

    public CoreConfig(String serverMode,
                      Locale language,
                      ZoneId timezone,
                      ArenaSettings arenaSettings,
                      ExecutorSettings executorSettings,
                      DatabaseSettings databaseSettings,
                      ServiceSettings serviceSettings,
                      TimeoutSettings timeoutSettings,
                      DegradedModeSettings degradedModeSettings,
                      QueueSettings queueSettings,
                      HologramSettings hologramSettings,
                      UiSettings uiSettings) {
        this.serverMode = Objects.requireNonNull(serverMode, "serverMode");
        this.language = Objects.requireNonNull(language, "language");
        this.timezone = Objects.requireNonNull(timezone, "timezone");
        this.arenaSettings = Objects.requireNonNull(arenaSettings, "arenaSettings");
        this.executorSettings = Objects.requireNonNull(executorSettings, "executorSettings");
        this.databaseSettings = Objects.requireNonNull(databaseSettings, "databaseSettings");
        this.serviceSettings = Objects.requireNonNull(serviceSettings, "serviceSettings");
        this.timeoutSettings = Objects.requireNonNull(timeoutSettings, "timeoutSettings");
        this.degradedModeSettings = Objects.requireNonNull(degradedModeSettings, "degradedModeSettings");
        this.queueSettings = Objects.requireNonNull(queueSettings, "queueSettings");
        this.hologramSettings = Objects.requireNonNull(hologramSettings, "hologramSettings");
        this.uiSettings = Objects.requireNonNull(uiSettings, "uiSettings");
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

    public ExecutorSettings executorSettings() {
        return executorSettings;
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

    public HologramSettings hologramSettings() {
        return hologramSettings;
    }

    public UiSettings uiSettings() {
        return uiSettings;
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

    public record ExecutorSettings(IoSettings io,
                                   ComputeSettings compute,
                                   ShutdownSettings shutdown,
                                   SchedulerSettings scheduler) {
        public ExecutorSettings {
            Objects.requireNonNull(io, "io");
            Objects.requireNonNull(compute, "compute");
            Objects.requireNonNull(shutdown, "shutdown");
            Objects.requireNonNull(scheduler, "scheduler");
        }

        public record IoSettings(boolean virtual, int maxThreads, long keepAliveMs) {
            public IoSettings {
                if (maxThreads <= 0) {
                    throw new IllegalArgumentException("maxThreads must be positive");
                }
                if (keepAliveMs < 0L) {
                    throw new IllegalArgumentException("keepAliveMs must be >= 0");
                }
            }
        }

        public record ComputeSettings(int size) {
            public ComputeSettings {
                if (size <= 0) {
                    throw new IllegalArgumentException("size must be positive");
                }
            }
        }

        public record ShutdownSettings(long awaitSeconds, long forceCancelSeconds) {
            public ShutdownSettings {
                if (awaitSeconds < 0L) {
                    throw new IllegalArgumentException("awaitSeconds must be >= 0");
                }
                if (forceCancelSeconds < 0L) {
                    throw new IllegalArgumentException("forceCancelSeconds must be >= 0");
                }
            }
        }

        public record SchedulerSettings(int mainCheckIntervalTicks) {
            public SchedulerSettings {
                if (mainCheckIntervalTicks <= 0) {
                    throw new IllegalArgumentException("mainCheckIntervalTicks must be > 0");
                }
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

    public record HologramSettings(int updateHz,
                                   int maxVisiblePerInstance,
                                   double lineSpacing,
                                   double viewRange,
                                   int maxPooledTextDisplays,
                                   int maxPooledInteractions) {
        public HologramSettings {
            if (updateHz <= 0) {
                throw new IllegalArgumentException("updateHz must be positive");
            }
            if (maxVisiblePerInstance <= 0) {
                throw new IllegalArgumentException("maxVisiblePerInstance must be positive");
            }
            if (lineSpacing <= 0D) {
                throw new IllegalArgumentException("lineSpacing must be > 0");
            }
            if (viewRange <= 0D) {
                throw new IllegalArgumentException("viewRange must be > 0");
            }
            if (maxPooledTextDisplays < 0) {
                throw new IllegalArgumentException("maxPooledTextDisplays must be >= 0");
            }
            if (maxPooledInteractions < 0) {
                throw new IllegalArgumentException("maxPooledInteractions must be >= 0");
            }
        }
    }

    public record TimeoutSettings(long startMs, long stopMs, WatchdogSettings watchdog) {
        public TimeoutSettings {
            if (startMs <= 0L) {
                throw new IllegalArgumentException("startMs must be positive");
            }
            if (stopMs <= 0L) {
                throw new IllegalArgumentException("stopMs must be positive");
            }
            Objects.requireNonNull(watchdog, "watchdog");
        }

        public record WatchdogSettings(long resetMs, long pasteMs) {
            public WatchdogSettings {
                if (resetMs <= 0L) {
                    throw new IllegalArgumentException("resetMs must be positive");
                }
                if (pasteMs <= 0L) {
                    throw new IllegalArgumentException("pasteMs must be positive");
                }
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

    public record UiSettings(boolean strictMiniMessage,
                             Map<String, TitleTimesProfile> titleProfiles,
                             BossBarDefaults bossBarDefaults) {
        public UiSettings {
            Objects.requireNonNull(titleProfiles, "titleProfiles");
            this.titleProfiles = Map.copyOf(titleProfiles);
            Objects.requireNonNull(bossBarDefaults, "bossBarDefaults");
        }
    }

    public record TitleTimesProfile(int fadeInTicks, int stayTicks, int fadeOutTicks) {
        public TitleTimesProfile {
            if (fadeInTicks < 0) {
                throw new IllegalArgumentException("fadeInTicks must be >= 0");
            }
            if (stayTicks <= 0) {
                throw new IllegalArgumentException("stayTicks must be > 0");
            }
            if (fadeOutTicks < 0) {
                throw new IllegalArgumentException("fadeOutTicks must be >= 0");
            }
        }
    }

    public record BossBarDefaults(BossBar.Color color,
                                  BossBar.Overlay overlay,
                                  Set<BossBar.Flag> flags,
                                  int updateEveryTicks) {
        public BossBarDefaults {
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(overlay, "overlay");
            Objects.requireNonNull(flags, "flags");
            this.flags = flags.isEmpty() ? Set.of() : Set.copyOf(flags);
            if (updateEveryTicks <= 0) {
                throw new IllegalArgumentException("updateEveryTicks must be > 0");
            }
        }
    }
}
