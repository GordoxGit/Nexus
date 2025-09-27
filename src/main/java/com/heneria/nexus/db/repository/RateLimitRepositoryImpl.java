package com.heneria.nexus.db.repository;

import com.heneria.nexus.db.ResilientDbExecutor;
import com.heneria.nexus.ratelimit.RateLimitResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MariaDB implementation of {@link RateLimitRepository} using optimistic locking.
 */
public final class RateLimitRepositoryImpl implements RateLimitRepository {

    private static final String SELECT_FOR_UPDATE_SQL =
            "SELECT last_executed_at FROM nexus_rate_limits WHERE player_uuid = ? AND action_key = ? FOR UPDATE";
    private static final String UPSERT_SQL =
            "INSERT INTO nexus_rate_limits (player_uuid, action_key, last_executed_at) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE last_executed_at = VALUES(last_executed_at)";
    private static final String DELETE_OLDER_THAN_SQL =
            "DELETE FROM nexus_rate_limits WHERE last_executed_at < ?";

    private final ResilientDbExecutor dbExecutor;

    public RateLimitRepositoryImpl(ResilientDbExecutor dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    @Override
    public CompletableFuture<RateLimitResult> checkAndRecord(UUID playerUuid, String actionKey, Duration cooldown) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(actionKey, "actionKey");
        Objects.requireNonNull(cooldown, "cooldown");
        return dbExecutor.execute("RateLimitRepository::checkAndRecord", connection -> executeCheck(connection, playerUuid, actionKey, cooldown));
    }

    private RateLimitResult executeCheck(Connection connection,
                                         UUID playerUuid,
                                         String actionKey,
                                         Duration cooldown) throws SQLException {
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
        } catch (SQLException ignored) {
            // Use default value if unsupported
        }
        connection.setAutoCommit(false);
        try {
            Instant now = Instant.now();
            try (PreparedStatement select = connection.prepareStatement(SELECT_FOR_UPDATE_SQL)) {
                select.setString(1, playerUuid.toString());
                select.setString(2, actionKey);
                try (ResultSet resultSet = select.executeQuery()) {
                    if (resultSet.next()) {
                        Instant lastExecution = resultSet.getTimestamp(1).toInstant();
                        Instant nextAllowed = lastExecution.plus(cooldown);
                        if (nextAllowed.isAfter(now)) {
                            connection.rollback();
                            Duration remaining = Duration.between(now, nextAllowed);
                            if (remaining.isNegative()) {
                                remaining = Duration.ZERO;
                            }
                            return RateLimitResult.blocked(remaining);
                        }
                    }
                }
            }
            try (PreparedStatement upsert = connection.prepareStatement(UPSERT_SQL)) {
                upsert.setString(1, playerUuid.toString());
                upsert.setString(2, actionKey);
                upsert.setTimestamp(3, Timestamp.from(now));
                upsert.executeUpdate();
            }
            connection.commit();
            return RateLimitResult.allowed();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // Ignore
            }
        }
    }

    @Override
    public CompletableFuture<Integer> purgeOlderThan(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return dbExecutor.execute("RateLimitRepository::purgeOlderThan", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_OLDER_THAN_SQL)) {
                statement.setTimestamp(1, Timestamp.from(cutoff));
                return statement.executeUpdate();
            }
        });
    }
}
