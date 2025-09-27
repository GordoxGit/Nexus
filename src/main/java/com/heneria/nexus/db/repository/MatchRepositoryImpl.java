package com.heneria.nexus.db.repository;

import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.match.MatchSnapshot;
import com.heneria.nexus.match.MatchSnapshot.ParticipantStats;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Default MariaDB-backed implementation of {@link MatchRepository}.
 */
public final class MatchRepositoryImpl implements MatchRepository {

    private static final String INSERT_MATCH_SQL =
            "INSERT INTO nexus_matches (match_id, map_id, arena_mode, start_timestamp, end_timestamp, winning_team) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String INSERT_PARTICIPANT_SQL =
            "INSERT INTO nexus_match_participants (match_id, player_uuid, team, kills, deaths, elo_change) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String PURGE_MATCHES_SQL =
            "DELETE FROM nexus_matches WHERE end_timestamp IS NOT NULL AND end_timestamp < ? LIMIT ?";

    private static final int PURGE_BATCH_SIZE = 1_000;

    private final DbProvider dbProvider;
    private final Executor ioExecutor;

    public MatchRepositoryImpl(DbProvider dbProvider, ExecutorManager executorManager) {
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.ioExecutor = Objects.requireNonNull(executorManager, "executorManager").io();
    }

    @Override
    public CompletableFuture<Void> save(MatchSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return dbProvider.execute(connection -> persistSnapshot(connection, snapshot), ioExecutor);
    }

    @Override
    public CompletableFuture<Integer> purgeOldMatches(Instant olderThan) {
        Objects.requireNonNull(olderThan, "olderThan");
        return dbProvider.execute(connection -> purgeMatches(connection, olderThan), ioExecutor);
    }

    private Void persistSnapshot(Connection connection, MatchSnapshot snapshot) throws SQLException {
        boolean previousAutoCommit = getAutoCommit(connection);
        connection.setAutoCommit(false);
        try {
            insertMatch(connection, snapshot);
            insertParticipants(connection, snapshot.matchId(), snapshot.participants());
            connection.commit();
            return null;
        } catch (Throwable throwable) {
            rollbackQuietly(connection);
            if (throwable instanceof SQLException exception) {
                throw exception;
            }
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new SQLException("Unexpected error while persisting match snapshot", throwable);
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
    }

    private void insertMatch(Connection connection, MatchSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MATCH_SQL)) {
            statement.setString(1, snapshot.matchId().toString());
            statement.setString(2, snapshot.mapId());
            statement.setString(3, snapshot.mode().name());
            statement.setTimestamp(4, Timestamp.from(snapshot.startTime()));
            statement.setTimestamp(5, Timestamp.from(snapshot.endTime()));
            statement.setString(6, snapshot.winningTeam().orElse(null));
            statement.executeUpdate();
        }
    }

    private void insertParticipants(Connection connection, java.util.UUID matchId, List<ParticipantStats> participants)
            throws SQLException {
        if (participants.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_PARTICIPANT_SQL)) {
            for (ParticipantStats participant : participants) {
                statement.setString(1, matchId.toString());
                statement.setString(2, participant.playerId().toString());
                statement.setString(3, participant.team());
                statement.setInt(4, participant.kills());
                statement.setInt(5, participant.deaths());
                statement.setInt(6, participant.eloChange());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private int purgeMatches(Connection connection, Instant olderThan) throws SQLException {
        Timestamp cutoff = Timestamp.from(olderThan);
        int totalDeleted = 0;
        try (PreparedStatement statement = connection.prepareStatement(PURGE_MATCHES_SQL)) {
            while (true) {
                statement.setTimestamp(1, cutoff);
                statement.setInt(2, PURGE_BATCH_SIZE);
                int deleted = statement.executeUpdate();
                totalDeleted += deleted;
                if (deleted < PURGE_BATCH_SIZE) {
                    break;
                }
            }
        }
        return totalDeleted;
    }

    private boolean getAutoCommit(Connection connection) {
        try {
            return connection.getAutoCommit();
        } catch (SQLException ignored) {
            return true;
        }
    }

    private void restoreAutoCommit(Connection connection, boolean value) {
        try {
            connection.setAutoCommit(value);
        } catch (SQLException ignored) {
            // Ignore
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Ignore
        }
    }
}
