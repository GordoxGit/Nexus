package com.heneria.nexus.service.maintenance;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.db.repository.MatchRepository;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Background service responsible for purging obsolete match history entries.
 */
public final class DataPurgeService implements LifecycleAware {

    private static final int SCHEDULE_HOUR = 4;
    private static final long MILLIS_PER_TICK = 50L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final MatchRepository matchRepository;
    private final AtomicReference<ServiceConfiguration> configurationRef;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object taskLock = new Object();

    private BukkitTask scheduledTask;

    public DataPurgeService(JavaPlugin plugin,
                            NexusLogger logger,
                            MatchRepository matchRepository,
                            CoreConfig coreConfig) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        Objects.requireNonNull(coreConfig, "coreConfig");
        this.configurationRef = new AtomicReference<>(toConfiguration(coreConfig));
    }

    @Override
    public CompletableFuture<Void> start() {
        running.set(true);
        scheduleNextRun();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        running.set(false);
        cancelScheduledTask();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Applies the latest configuration after a reload.
     */
    public void applyConfiguration(CoreConfig coreConfig) {
        Objects.requireNonNull(coreConfig, "coreConfig");
        ServiceConfiguration newConfig = toConfiguration(coreConfig);
        ServiceConfiguration previous = configurationRef.getAndSet(newConfig);
        if (!running.get()) {
            return;
        }
        if (!newConfig.equals(previous)) {
            scheduleNextRun();
        }
    }

    private ServiceConfiguration toConfiguration(CoreConfig coreConfig) {
        CoreConfig.DatabaseSettings database = coreConfig.databaseSettings();
        return new ServiceConfiguration(
                database.enabled(),
                coreConfig.timezone(),
                Math.max(0, database.retentionPolicy().matchHistoryDays()));
    }

    private void scheduleNextRun() {
        if (!running.get()) {
            return;
        }
        ServiceConfiguration config = configurationRef.get();
        if (!config.databaseEnabled()) {
            logger.info("Purge automatique des matchs désactivée (MariaDB désactivée)");
            cancelScheduledTask();
            return;
        }
        if (config.retentionDays() <= 0) {
            logger.info("Purge automatique des matchs désactivée (match_history_days <= 0)");
            cancelScheduledTask();
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(config.timezone());
        ZonedDateTime nextRun = now.withHour(SCHEDULE_HOUR).withMinute(0).withSecond(0).withNano(0);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        long delayTicks = Math.max(1L,
                (Duration.between(now, nextRun).toMillis() + (MILLIS_PER_TICK - 1)) / MILLIS_PER_TICK);
        synchronized (taskLock) {
            if (!running.get()) {
                return;
            }
            cancelScheduledTaskLocked();
            scheduledTask = plugin.getServer().getScheduler()
                    .runTaskLaterAsynchronously(plugin, this::executePurge, delayTicks);
        }
        ZonedDateTime finalNextRun = nextRun;
        logger.info(() -> "Purge automatique des matchs planifiée le "
                + DATE_TIME_FORMATTER.format(finalNextRun) + " (rétention "
                + config.retentionDays() + "j)");
    }

    private void executePurge() {
        synchronized (taskLock) {
            scheduledTask = null;
        }
        if (!running.get()) {
            return;
        }
        ServiceConfiguration config = configurationRef.get();
        if (!config.databaseEnabled()) {
            logger.info("Purge automatique des matchs ignorée (MariaDB désactivée)");
            if (running.get()) {
                scheduleNextRun();
            }
            return;
        }
        if (config.retentionDays() <= 0) {
            logger.info("Purge automatique des matchs ignorée (match_history_days <= 0)");
            if (running.get()) {
                scheduleNextRun();
            }
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(config.retentionDays()));
        matchRepository.purgeOldMatches(cutoff)
                .whenComplete((deleted, throwable) -> {
                    if (throwable != null) {
                        logger.warn("Purge automatique des matchs en échec", unwrap(throwable));
                    } else {
                        logger.info(() -> "Purge automatique des matchs terminée — "
                                + deleted + " match(s) supprimé(s) (rétention "
                                + config.retentionDays() + "j)");
                    }
                    if (running.get()) {
                        scheduleNextRun();
                    }
                });
    }

    private void cancelScheduledTask() {
        synchronized (taskLock) {
            cancelScheduledTaskLocked();
        }
    }

    private void cancelScheduledTaskLocked() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    private Throwable unwrap(Throwable throwable) {
        while ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
                && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }

    private record ServiceConfiguration(boolean databaseEnabled, ZoneId timezone, int retentionDays) {
    }
}
