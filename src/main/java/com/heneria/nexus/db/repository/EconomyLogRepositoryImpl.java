package com.heneria.nexus.db.repository;

import com.heneria.nexus.db.ResilientDbExecutor;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default MariaDB implementation of {@link EconomyLogRepository}.
 */
public final class EconomyLogRepositoryImpl implements EconomyLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO nexus_economy_log (player_uuid, transaction_type, amount, balance_after, reason)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final ResilientDbExecutor dbExecutor;

    public EconomyLogRepositoryImpl(ResilientDbExecutor dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    @Override
    public CompletableFuture<Void> insert(UUID playerUuid,
                                          long amount,
                                          long balanceAfter,
                                          String transactionType,
                                          String reason) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(transactionType, "transactionType");
        return dbExecutor.execute("EconomyLogRepository::insert", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, transactionType);
                statement.setLong(3, amount);
                statement.setLong(4, balanceAfter);
                if (reason == null || reason.isBlank()) {
                    statement.setNull(5, Types.VARCHAR);
                } else {
                    statement.setString(5, reason);
                }
                statement.executeUpdate();
            }
            return null;
        });
    }
}
