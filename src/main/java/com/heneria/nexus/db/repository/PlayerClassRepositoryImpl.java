package com.heneria.nexus.db.repository;

import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Default MariaDB-backed implementation of {@link PlayerClassRepository}.
 */
public final class PlayerClassRepositoryImpl implements PlayerClassRepository {

    private static final String SELECT_UNLOCK_SQL =
            "SELECT is_unlocked FROM nexus_player_classes WHERE player_uuid = ? AND class_id = ?";
    private static final String UPSERT_UNLOCK_SQL =
            "INSERT INTO nexus_player_classes (player_uuid, class_id, class_xp, is_unlocked) " +
                    "VALUES (?, ?, ?, TRUE) " +
                    "ON DUPLICATE KEY UPDATE is_unlocked = VALUES(is_unlocked)";

    private final DbProvider dbProvider;
    private final Executor ioExecutor;

    public PlayerClassRepositoryImpl(DbProvider dbProvider, ExecutorManager executorManager) {
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.ioExecutor = Objects.requireNonNull(executorManager, "executorManager").io();
    }

    @Override
    public CompletableFuture<Boolean> isUnlocked(UUID playerUuid, String classId) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(classId, "classId");
        return dbProvider.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_UNLOCK_SQL)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, classId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return false;
                    }
                    return resultSet.getBoolean("is_unlocked");
                }
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> unlock(UUID playerUuid, String classId) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(classId, "classId");
        return dbProvider.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_UNLOCK_SQL)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, classId);
                statement.setLong(3, 0L);
                statement.executeUpdate();
            }
            return null;
        }, ioExecutor);
    }
}
