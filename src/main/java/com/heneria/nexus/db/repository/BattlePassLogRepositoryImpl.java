package com.heneria.nexus.db.repository;

import com.heneria.nexus.db.ResilientDbExecutor;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default MariaDB implementation of {@link BattlePassLogRepository}.
 */
public final class BattlePassLogRepositoryImpl implements BattlePassLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO nexus_battle_pass_xp_log (player_uuid, xp_delta, reason)
            VALUES (?, ?, ?)
            """;

    private final ResilientDbExecutor dbExecutor;

    public BattlePassLogRepositoryImpl(ResilientDbExecutor dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    @Override
    public CompletableFuture<Void> insert(UUID playerUuid, int xpDelta, String reason) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return dbExecutor.execute("BattlePassLogRepository::insert", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                statement.setString(1, playerUuid.toString());
                statement.setInt(2, xpDelta);
                if (reason == null || reason.isBlank()) {
                    statement.setNull(3, Types.VARCHAR);
                } else {
                    statement.setString(3, reason);
                }
                statement.executeUpdate();
            }
            return null;
        });
    }
}
