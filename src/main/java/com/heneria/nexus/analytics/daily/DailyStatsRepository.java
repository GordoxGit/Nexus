package com.heneria.nexus.analytics.daily;

import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Repository responsible for persisting aggregated daily statistics.
 */
public final class DailyStatsRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO nexus_daily_stats (stat_date, total_matches_played, total_players_unique,
                                           total_coins_earned, total_bpxp_earned)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                total_matches_played = VALUES(total_matches_played),
                total_players_unique = VALUES(total_players_unique),
                total_coins_earned = VALUES(total_coins_earned),
                total_bpxp_earned = VALUES(total_bpxp_earned)
            """;

    private final DbProvider dbProvider;
    private final Executor ioExecutor;

    public DailyStatsRepository(DbProvider dbProvider, ExecutorManager executorManager) {
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.ioExecutor = Objects.requireNonNull(executorManager, "executorManager").io();
    }

    public CompletableFuture<Void> saveOrUpdate(DailyStatsSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return dbProvider.execute("DailyStatsRepository::saveOrUpdate",
                connection -> persist(connection, snapshot), ioExecutor);
    }

    private Void persist(Connection connection, DailyStatsSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setDate(1, Date.valueOf(snapshot.statDate()));
            statement.setInt(2, snapshot.totalMatchesPlayed());
            statement.setInt(3, snapshot.totalPlayersUnique());
            statement.setLong(4, snapshot.totalCoinsEarned());
            statement.setLong(5, snapshot.totalBattlePassXpEarned());
            statement.executeUpdate();
        }
        return null;
    }
}
