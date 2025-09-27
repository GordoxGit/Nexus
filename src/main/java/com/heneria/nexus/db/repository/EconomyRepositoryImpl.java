package com.heneria.nexus.db.repository;

import com.heneria.nexus.api.EconomyTransferResult;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Default MariaDB-backed implementation of {@link EconomyRepository}.
 */
public final class EconomyRepositoryImpl implements EconomyRepository {

    private static final String SELECT_BALANCE_SQL =
            "SELECT balance FROM nexus_economy WHERE player_uuid = ?";
    private static final String SELECT_BALANCE_FOR_UPDATE_SQL =
            "SELECT balance FROM nexus_economy WHERE player_uuid = ? FOR UPDATE";
    private static final String UPDATE_BALANCE_SQL =
            "UPDATE nexus_economy SET balance = ? WHERE player_uuid = ?";
    private static final String INSERT_BALANCE_SQL =
            "INSERT INTO nexus_economy (player_uuid, balance) VALUES (?, ?)";
    private static final String UPSERT_BALANCE_SQL =
            "INSERT INTO nexus_economy (player_uuid, balance) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE balance = VALUES(balance)";

    private final DbProvider dbProvider;
    private final Executor ioExecutor;

    public EconomyRepositoryImpl(DbProvider dbProvider, ExecutorManager executorManager) {
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.ioExecutor = Objects.requireNonNull(executorManager, "executorManager").io();
    }

    @Override
    public CompletableFuture<Long> getBalance(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return dbProvider.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_BALANCE_SQL)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getLong("balance");
                    }
                    return 0L;
                }
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Long> setBalance(UUID playerUuid, long balance) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (balance < 0L) {
            throw new IllegalArgumentException("balance must be >= 0");
        }
        return dbProvider.execute(connection -> {
            try (PreparedStatement update = connection.prepareStatement(UPDATE_BALANCE_SQL)) {
                update.setLong(1, balance);
                update.setString(2, playerUuid.toString());
                int affected = update.executeUpdate();
                if (affected == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(INSERT_BALANCE_SQL)) {
                        insert.setString(1, playerUuid.toString());
                        insert.setLong(2, balance);
                        insert.executeUpdate();
                    }
                }
            }
            return balance;
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Long> addToBalance(UUID playerUuid, long amount) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return dbProvider.execute(connection -> adjustBalance(connection, playerUuid, amount), ioExecutor);
    }

    @Override
    public CompletableFuture<EconomyTransferResult> transfer(UUID from, UUID to, long amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (amount < 0L) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        return dbProvider.execute(connection -> transferInternal(connection, from, to, amount), ioExecutor);
    }

    @Override
    public CompletableFuture<Void> saveAll(Map<UUID, Long> balances) {
        Objects.requireNonNull(balances, "balances");
        if (balances.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return dbProvider.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_BALANCE_SQL)) {
                for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
                    statement.setString(1, entry.getKey().toString());
                    statement.setLong(2, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }, ioExecutor);
    }

    private long adjustBalance(Connection connection, UUID playerUuid, long amount) throws SQLException {
        boolean previousAutoCommit = getAutoCommit(connection);
        connection.setAutoCommit(false);
        try {
            BalanceSnapshot snapshot = lockBalance(connection, playerUuid);
            long next = snapshot.balance() + amount;
            if (next < 0L) {
                throw new IllegalStateException("Solde insuffisant pour " + playerUuid);
            }
            persistBalance(connection, playerUuid, next, snapshot.exists());
            connection.commit();
            return next;
        } catch (Throwable throwable) {
            rollbackQuietly(connection);
            throw throwable;
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
    }

    private EconomyTransferResult transferInternal(Connection connection, UUID from, UUID to, long amount) throws SQLException {
        boolean previousAutoCommit = getAutoCommit(connection);
        connection.setAutoCommit(false);
        try {
            BalanceSnapshot fromSnapshot = lockBalance(connection, from);
            BalanceSnapshot toSnapshot = lockBalance(connection, to);
            long nextFrom = fromSnapshot.balance() - amount;
            if (nextFrom < 0L) {
                throw new IllegalStateException("Solde insuffisant pour " + from);
            }
            long nextTo = toSnapshot.balance() + amount;
            persistBalance(connection, from, nextFrom, fromSnapshot.exists());
            persistBalance(connection, to, nextTo, toSnapshot.exists());
            connection.commit();
            return new EconomyTransferResult(nextFrom, nextTo);
        } catch (Throwable throwable) {
            rollbackQuietly(connection);
            throw throwable;
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
    }

    private BalanceSnapshot lockBalance(Connection connection, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BALANCE_FOR_UPDATE_SQL)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new BalanceSnapshot(true, resultSet.getLong("balance"));
                }
            }
        }
        return new BalanceSnapshot(false, 0L);
    }

    private void persistBalance(Connection connection, UUID playerUuid, long balance, boolean existed) throws SQLException {
        if (existed) {
            try (PreparedStatement update = connection.prepareStatement(UPDATE_BALANCE_SQL)) {
                update.setLong(1, balance);
                update.setString(2, playerUuid.toString());
                int affected = update.executeUpdate();
                if (affected > 0) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(INSERT_BALANCE_SQL)) {
            insert.setString(1, playerUuid.toString());
            insert.setLong(2, balance);
            insert.executeUpdate();
        }
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

    private record BalanceSnapshot(boolean exists, long balance) {
    }
}
