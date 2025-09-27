package com.heneria.nexus.db.repository;

import com.heneria.nexus.api.PlayerProfile;
import com.heneria.nexus.db.ResilientDbExecutor;
import com.heneria.nexus.db.OptimisticLockException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default MariaDB-backed implementation of {@link ProfileRepository}.
 */
public final class ProfileRepositoryImpl implements ProfileRepository {

    private static final String SELECT_PROFILE_SQL =
            "SELECT elo_rating, total_kills, total_deaths, total_wins, total_losses, matches_played, version " +
                    "FROM nexus_profiles WHERE player_uuid = ?";
    private static final String INSERT_PROFILE_SQL =
            "INSERT INTO nexus_profiles (player_uuid, elo_rating, total_kills, total_deaths, total_wins, total_losses, matches_played, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_PROFILE_SQL =
            "UPDATE nexus_profiles SET elo_rating = ?, total_kills = ?, total_deaths = ?, total_wins = ?, total_losses = ?, " +
                    "matches_played = ?, version = ? WHERE player_uuid = ? AND version = ?";

    private static final String STAT_ELO = "elo_rating";
    private static final String STAT_TOTAL_KILLS = "total_kills";
    private static final String STAT_TOTAL_DEATHS = "total_deaths";
    private static final String STAT_TOTAL_WINS = "total_wins";
    private static final String STAT_TOTAL_LOSSES = "total_losses";
    private static final String STAT_MATCHES_PLAYED = "matches_played";

    private final ResilientDbExecutor dbExecutor;

    public ProfileRepositoryImpl(ResilientDbExecutor dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findByUuid(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return dbExecutor.execute(connection -> {
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
                    int version = resultSet.getInt("version");
                    PlayerProfile profile = new PlayerProfile(
                            playerUuid,
                            statistics,
                            new ConcurrentHashMap<>(),
                            new ArrayList<>(),
                            Instant.now(),
                            version);
                    return Optional.of(profile);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> createOrUpdate(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (profile.getVersion() == profile.getPersistedVersion()) {
            return CompletableFuture.completedFuture(null);
        }
        return dbExecutor.execute(connection -> {
            try {
                if (profile.getPersistedVersion() == 0) {
                    try (PreparedStatement statement = connection.prepareStatement(INSERT_PROFILE_SQL)) {
                        populateInsertStatement(statement, profile);
                        statement.executeUpdate();
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement(UPDATE_PROFILE_SQL)) {
                        populateUpdateStatement(statement, profile);
                        int affectedRows = statement.executeUpdate();
                        if (affectedRows == 0) {
                            throw new OptimisticLockException(
                                    "Conflit de concurrence détecté pour le profil : " + profile.playerId());
                        }
                    }
                }
            } catch (SQLIntegrityConstraintViolationException exception) {
                throw new OptimisticLockException(
                        "Conflit lors de la sauvegarde du profil : " + profile.playerId(), exception);
            }
            profile.markPersisted();
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveAll(Collection<PlayerProfile> profiles) {
        Objects.requireNonNull(profiles, "profiles");
        if (profiles.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return dbExecutor.execute(connection -> {
            try (PreparedStatement insertStatement = connection.prepareStatement(INSERT_PROFILE_SQL);
                 PreparedStatement updateStatement = connection.prepareStatement(UPDATE_PROFILE_SQL)) {
                for (PlayerProfile profile : profiles) {
                    if (profile.getVersion() == profile.getPersistedVersion()) {
                        continue;
                    }
                    try {
                        if (profile.getPersistedVersion() == 0) {
                            populateInsertStatement(insertStatement, profile);
                            insertStatement.executeUpdate();
                        } else {
                            populateUpdateStatement(updateStatement, profile);
                            int affectedRows = updateStatement.executeUpdate();
                            if (affectedRows == 0) {
                                throw new OptimisticLockException(
                                        "Conflit de concurrence détecté pour le profil : " + profile.playerId());
                            }
                        }
                        profile.markPersisted();
                    } catch (SQLIntegrityConstraintViolationException exception) {
                        throw new OptimisticLockException(
                                "Conflit lors de la sauvegarde du profil : " + profile.playerId(), exception);
                    }
                }
            }
            return null;
        });
    }

    private void populateInsertStatement(PreparedStatement statement, PlayerProfile profile) throws SQLException {
        Map<String, Long> statistics = profile.statistics();
        statement.setString(1, profile.playerId().toString());
        statement.setInt(2, longToInt(statistics.getOrDefault(STAT_ELO, 1000L)));
        statement.setInt(3, longToInt(statistics.getOrDefault(STAT_TOTAL_KILLS, 0L)));
        statement.setInt(4, longToInt(statistics.getOrDefault(STAT_TOTAL_DEATHS, 0L)));
        statement.setInt(5, longToInt(statistics.getOrDefault(STAT_TOTAL_WINS, 0L)));
        statement.setInt(6, longToInt(statistics.getOrDefault(STAT_TOTAL_LOSSES, 0L)));
        statement.setInt(7, longToInt(statistics.getOrDefault(STAT_MATCHES_PLAYED, 0L)));
        statement.setInt(8, profile.getVersion());
    }

    private void populateUpdateStatement(PreparedStatement statement, PlayerProfile profile) throws SQLException {
        Map<String, Long> statistics = profile.statistics();
        statement.setInt(1, longToInt(statistics.getOrDefault(STAT_ELO, 1000L)));
        statement.setInt(2, longToInt(statistics.getOrDefault(STAT_TOTAL_KILLS, 0L)));
        statement.setInt(3, longToInt(statistics.getOrDefault(STAT_TOTAL_DEATHS, 0L)));
        statement.setInt(4, longToInt(statistics.getOrDefault(STAT_TOTAL_WINS, 0L)));
        statement.setInt(5, longToInt(statistics.getOrDefault(STAT_TOTAL_LOSSES, 0L)));
        statement.setInt(6, longToInt(statistics.getOrDefault(STAT_MATCHES_PLAYED, 0L)));
        statement.setInt(7, profile.getVersion());
        statement.setString(8, profile.playerId().toString());
        statement.setInt(9, profile.getPersistedVersion());
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
