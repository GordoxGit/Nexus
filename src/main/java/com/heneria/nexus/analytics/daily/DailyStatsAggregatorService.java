package com.heneria.nexus.analytics.daily;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.NexusLogger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodically aggregates daily statistics and stores them in the {@code nexus_daily_stats} table.
 */
public final class DailyStatsAggregatorService implements LifecycleAware {

    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    private static final String MATCHES_COUNT_SQL = """
            SELECT COUNT(*)
            FROM nexus_matches
            WHERE end_timestamp IS NOT NULL
              AND end_timestamp >= ?
              AND end_timestamp < ?
            """;

    private static final String UNIQUE_PLAYERS_SQL = """
            SELECT COUNT(DISTINCT p.player_uuid)
            FROM nexus_match_participants p
            INNER JOIN nexus_matches m ON m.match_id = p.match_id
            WHERE m.end_timestamp IS NOT NULL
              AND m.end_timestamp >= ?
              AND m.end_timestamp < ?
            """;

    private static final List<String> COINS_SQL_CANDIDATES = List.of(
            "SELECT COALESCE(SUM(delta), 0) FROM nexus_economy_log WHERE delta > 0 AND created_at >= ? AND created_at < ?",
            "SELECT COALESCE(SUM(amount_delta), 0) FROM nexus_economy_log WHERE amount_delta > 0 AND created_at >= ? AND created_at < ?",
            "SELECT COALESCE(SUM(amount), 0) FROM nexus_economy_log WHERE amount > 0 AND created_at >= ? AND created_at < ?"
    );

    private static final List<String> BPXP_SQL_CANDIDATES = List.of(
            "SELECT COALESCE(SUM(xp_delta), 0) FROM nexus_battle_pass_xp_log WHERE xp_delta > 0 AND created_at >= ? AND created_at < ?",
            "SELECT COALESCE(SUM(delta), 0) FROM nexus_battle_pass_xp_log WHERE delta > 0 AND created_at >= ? AND created_at < ?",
            "SELECT COALESCE(SUM(xp_amount), 0) FROM nexus_battle_pass_xp_log WHERE xp_amount > 0 AND created_at >= ? AND created_at < ?",
            "SELECT COALESCE(SUM(xp_delta), 0) FROM nexus_battle_pass_log WHERE xp_delta > 0 AND created_at >= ? AND created_at < ?"
    );

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final DbProvider dbProvider;
    private final DailyStatsRepository repository;
    private final Executor ioExecutor;
    private final ZoneId zoneId;
    private final boolean enabled;
    private final Set<String> suppressedWarnings = ConcurrentHashMap.newKeySet();
    private final Object chainLock = new Object();

    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final AtomicReference<BukkitTask> readinessTask = new AtomicReference<>();
    private final AtomicReference<BukkitTask> scheduledTask = new AtomicReference<>();
    private volatile CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

    public DailyStatsAggregatorService(JavaPlugin plugin,
                                       NexusLogger logger,
                                       DbProvider dbProvider,
                                       DailyStatsRepository repository,
                                       ExecutorManager executorManager,
                                       CoreConfig coreConfig) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ioExecutor = Objects.requireNonNull(executorManager, "executorManager").io();
        this.zoneId = DEFAULT_ZONE;
        this.enabled = Objects.requireNonNull(coreConfig, "coreConfig").databaseSettings().enabled();
    }

    @Override
    public CompletableFuture<Void> start() {
        if (!enabled) {
            logger.info("Agrégation quotidienne désactivée (base de données inopérante)");
            return CompletableFuture.completedFuture(null);
        }
        if (!isDatabaseReady()) {
            logger.info("Agrégateur quotidien en attente de la disponibilité de la base de données");
            scheduleDatabaseAvailabilityCheck();
            return CompletableFuture.completedFuture(null);
        }
        startAggregationLifecycle();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        cancelReadinessTask();
        cancelScheduledTask();
        CompletableFuture<Void> pending;
        synchronized (chainLock) {
            pending = tail;
        }
        return pending.exceptionally(throwable -> null);
    }

    @Override
    public boolean isHealthy() {
        return lastError.get() == null;
    }

    @Override
    public java.util.Optional<Throwable> lastError() {
        return java.util.Optional.ofNullable(lastError.get());
    }

    /**
     * Triggers aggregation for the provided date.
     */
    public CompletableFuture<Void> aggregateFor(LocalDate date) {
        Objects.requireNonNull(date, "date");
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }
        if (date.isAfter(LocalDate.now(zoneId))) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Impossible d'agréger une date future"));
        }
        CompletableFuture<Void> result;
        synchronized (chainLock) {
            tail = tail.exceptionally(throwable -> null)
                    .thenCompose(ignored -> performAggregation(date));
            result = tail;
        }
        return result;
    }

    /**
     * Immediately launches aggregation for the previous day without waiting for the scheduled run.
     */
    public CompletableFuture<Void> aggregateYesterday() {
        return aggregateFor(LocalDate.now(zoneId).minusDays(1));
    }

    private void startAggregationLifecycle() {
        cancelReadinessTask();
        scheduleNextRun();
        LocalDate yesterday = LocalDate.now(zoneId).minusDays(1);
        aggregateFor(yesterday).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                logger.warn("Agrégation quotidienne initiale en échec", unwrap(throwable));
            }
        });
        logger.info("Agrégateur de statistiques quotidiennes initialisé");
    }

    private CompletableFuture<Void> performAggregation(LocalDate targetDate) {
        return dbProvider.execute("DailyStatsAggregatorService::computeSnapshot",
                connection -> computeSnapshot(connection, targetDate), ioExecutor)
                .thenCompose(repository::saveOrUpdate)
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        lastError.set(unwrap(throwable));
                        logger.warn("Échec de l'agrégation quotidienne pour " + targetDate, unwrap(throwable));
                    } else {
                        lastError.set(null);
                        logger.debug(() -> "Statistiques quotidiennes agrégées pour " + targetDate);
                    }
                });
    }

    private DailyStatsSnapshot computeSnapshot(Connection connection, LocalDate date) throws SQLException {
        Timestamp start = Timestamp.from(date.atStartOfDay(zoneId).toInstant());
        Timestamp end = Timestamp.from(date.plusDays(1).atStartOfDay(zoneId).toInstant());

        int matches = queryCount(connection, MATCHES_COUNT_SQL, start, end);
        int uniquePlayers = queryCount(connection, UNIQUE_PLAYERS_SQL, start, end);
        long coins = queryMetricWithFallback(connection, COINS_SQL_CANDIDATES, start, end, "coins");
        long battlePassXp = queryMetricWithFallback(connection, BPXP_SQL_CANDIDATES, start, end, "battle pass xp");

        if (coins < 0L) {
            coins = 0L;
        }
        if (battlePassXp < 0L) {
            battlePassXp = 0L;
        }

        return new DailyStatsSnapshot(date, matches, uniquePlayers, coins, battlePassXp);
    }

    private int queryCount(Connection connection, String sql, Timestamp start, Timestamp end) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, start);
            statement.setTimestamp(2, end);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }
        return 0;
    }

    private long queryMetricWithFallback(Connection connection,
                                         List<String> candidates,
                                         Timestamp start,
                                         Timestamp end,
                                         String metricName) {
        List<SQLException> failures = new ArrayList<>();
        for (String sql : candidates) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, start);
                statement.setTimestamp(2, end);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Math.max(0L, resultSet.getLong(1));
                    }
                }
            } catch (SQLException exception) {
                failures.add(exception);
            }
        }
        if (!failures.isEmpty()) {
            SQLException last = failures.getLast();
            String sqlState = last.getSQLState();
            String key = metricName + ':' + (sqlState != null ? sqlState : last.getClass().getName());
            if (suppressedWarnings.add(key)) {
                logger.warn("Impossible de calculer la métrique %s (utilisation de 0)".formatted(metricName), last);
            } else {
                logger.debug(() -> "Échec répété pour la métrique %s: %s".formatted(metricName, last.getMessage()));
            }
        }
        return 0L;
    }

    private void scheduleDatabaseAvailabilityCheck() {
        if (readinessTask.get() != null) {
            return;
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.isEnabled() || !enabled) {
                cancelReadinessTask();
                return;
            }
            if (isDatabaseReady()) {
                cancelReadinessTask();
                startAggregationLifecycle();
            }
        }, 20L, 20L);
        if (!readinessTask.compareAndSet(null, task)) {
            task.cancel();
        }
    }

    private boolean isDatabaseReady() {
        return dbProvider.isReady();
    }

    private void scheduleNextRun() {
        cancelScheduledTask();
        long delayTicks = Math.max(1L, (computeDelayToNextRun().toMillis() + 49L) / 50L);
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskLaterAsynchronously(plugin, this::runScheduledAggregation, delayTicks);
        scheduledTask.set(task);
    }

    private void runScheduledAggregation() {
        if (!plugin.isEnabled()) {
            return;
        }
        aggregateYesterday().whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                logger.warn("Exécution planifiée de l'agrégateur en échec", unwrap(throwable));
            }
            scheduleNextRun();
        });
    }

    private Duration computeDelayToNextRun() {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime nextRun = now.truncatedTo(ChronoUnit.DAYS)
                .plusDays(1)
                .withHour(0)
                .withMinute(5)
                .withSecond(0)
                .withNano(0);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun);
    }

    private void cancelReadinessTask() {
        BukkitTask task = readinessTask.getAndSet(null);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelScheduledTask() {
        BukkitTask task = scheduledTask.getAndSet(null);
        if (task != null) {
            task.cancel();
        }
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return throwable;
    }
}
