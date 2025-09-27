package com.heneria.nexus.db.repository;

import com.heneria.nexus.api.PlayerProfile;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Default MariaDB-backed implementation of {@link ProfileRepository}.
 */
public final class ProfileRepositoryImpl implements ProfileRepository {

    private static final String SELECT_PROFILE_SQL =
            "SELECT elo_rating, total_kills, total_deaths, total_wins, total_losses, matches_played " +
                    "FROM nexus_profiles WHERE player_uuid = ?";
    private static final String UPSERT_PROFILE_SQL =
            "INSERT INTO nexus_profiles (player_uuid, elo_rating, total_kills, total_deaths, total_wins, total_losses, matches_played) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "elo_rating = VALUES(elo_rating), " +
                    "total_kills = VALUES(total_kills), " +
                    "total_deaths = VALUES(total_deaths), " +
                    "total_wins = VALUES(total_wins), " +
                    "total_losses = VALUES(total_losses), " +
                    "matches_played = VALUES(matches_played)";

    private static final String STAT_ELO = "elo_rating";
    private static final String STAT_TOTAL_KILLS = "total_kills";
    private static final String STAT_TOTAL_DEATHS = "total_deaths";
    private static final String STAT_TOTAL_WINS = "total_wins";
    private static final String STAT_TOTAL_LOSSES = "total_losses";
    private static final String STAT_MATCHES_PLAYED = "matches_played";

    private final DbProvider dbProvider;
    private final Executor ioExecutor;

    public ProfileRepositoryImpl(DbProvider dbProvider, ExecutorManager executorManager) {
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.ioExecutor = Objects.requireNonNull(executorManager, "executorManager").io();
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findByUuid(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return dbProvider.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_PROFILE_SQL)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    Map<String, Long> statistics = new ConcurrentHashMap<>();
                    statistics.put(STAT_ELO, (long) resultSet.getInt("elo_rating"));
                    statistics.put(STAT_TOTAL_KILLS, (long) resultSet.getInt("total_kills"));
                    statistics.put(STAT_TOTAL_DEATHS, (long) resultSet.getInt("total_deaths"));
                    statistics.put(STAT_TOTAL_WINS, (long) resultSet.getInt("total_wins"));
                    statistics.put(STAT_TOTAL_LOSSES, (long) resultSet.getInt("total_losses"));
                    statistics.put(STAT_MATCHES_PLAYED, (long) resultSet.getInt("matches_played"));
                    PlayerProfile profile = new PlayerProfile(
                            playerUuid,
                            statistics,
                            new ConcurrentHashMap<>(),
                            new ArrayList<>(),
                            Instant.now());
                    return Optional.of(profile);
                }
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> createOrUpdate(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        Map<String, Long> statistics = profile.statistics();
        return dbProvider.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_PROFILE_SQL)) {
                statement.setString(1, profile.playerId().toString());
                statement.setInt(2, longToInt(statistics.getOrDefault(STAT_ELO, 1000L)));
                statement.setInt(3, longToInt(statistics.getOrDefault(STAT_TOTAL_KILLS, 0L)));
                statement.setInt(4, longToInt(statistics.getOrDefault(STAT_TOTAL_DEATHS, 0L)));
                statement.setInt(5, longToInt(statistics.getOrDefault(STAT_TOTAL_WINS, 0L)));
                statement.setInt(6, longToInt(statistics.getOrDefault(STAT_TOTAL_LOSSES, 0L)));
                statement.setInt(7, longToInt(statistics.getOrDefault(STAT_MATCHES_PLAYED, 0L)));
                statement.executeUpdate();
            }
            return null;
        }, ioExecutor);
    }

    private int longToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }
}
