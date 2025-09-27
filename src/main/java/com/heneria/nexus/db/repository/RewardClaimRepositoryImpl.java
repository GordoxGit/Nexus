package com.heneria.nexus.db.repository;

import com.heneria.nexus.db.ResilientDbExecutor;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MariaDB-backed implementation persisting claimed reward keys.
 */
public final class RewardClaimRepositoryImpl implements RewardClaimRepository {

    private static final int ERROR_DUPLICATE_ENTRY = 1062;
    private static final String SQL_STATE_INTEGRITY_CONSTRAINT = "23000";
    private static final String INSERT_CLAIM_SQL =
            "INSERT INTO nexus_rewards_claimed (player_uuid, reward_key) VALUES (?, ?)";

    private final ResilientDbExecutor dbExecutor;

    public RewardClaimRepositoryImpl(ResilientDbExecutor dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    @Override
    public CompletableFuture<Boolean> tryClaim(UUID playerUuid, String rewardKey) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(rewardKey, "rewardKey");
        return dbExecutor.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_CLAIM_SQL)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, rewardKey);
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                if (isDuplicateClaim(exception)) {
                    return false;
                }
                throw exception;
            }
        });
    }

    private boolean isDuplicateClaim(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            if (current.getErrorCode() == ERROR_DUPLICATE_ENTRY) {
                return true;
            }
            String sqlState = current.getSQLState();
            if (sqlState != null && SQL_STATE_INTEGRITY_CONSTRAINT.equals(sqlState)) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }
}
