package com.heneria.nexus.db.repository;

import com.heneria.nexus.db.ResilientDbExecutor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default MariaDB-backed implementation of {@link PlayerCosmeticRepository}.
 */
public final class PlayerCosmeticRepositoryImpl implements PlayerCosmeticRepository {

    private static final String SELECT_UNLOCK_SQL =
            "SELECT 1 FROM nexus_player_cosmetics WHERE player_uuid = ? AND cosmetic_id = ?";
    private static final String UPSERT_UNLOCK_SQL =
            "INSERT INTO nexus_player_cosmetics (player_uuid, cosmetic_id, cosmetic_type) " +
                    "VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE cosmetic_type = VALUES(cosmetic_type)";

    private final ResilientDbExecutor dbExecutor;

    public PlayerCosmeticRepositoryImpl(ResilientDbExecutor dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    @Override
    public CompletableFuture<Boolean> isUnlocked(UUID playerUuid, String cosmeticId) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(cosmeticId, "cosmeticId");
        return dbExecutor.execute("PlayerCosmeticRepository::isUnlocked", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_UNLOCK_SQL)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, cosmeticId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> unlock(UUID playerUuid, String cosmeticId, String cosmeticType) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(cosmeticId, "cosmeticId");
        Objects.requireNonNull(cosmeticType, "cosmeticType");
        return dbExecutor.execute("PlayerCosmeticRepository::unlock", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_UNLOCK_SQL)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, cosmeticId);
                statement.setString(3, cosmeticType);
                statement.executeUpdate();
            }
            return null;
        });
    }
}
