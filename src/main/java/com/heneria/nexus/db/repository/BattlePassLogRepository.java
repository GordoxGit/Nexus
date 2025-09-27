package com.heneria.nexus.db.repository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists audit entries for Battle Pass experience gains.
 */
public interface BattlePassLogRepository {

    /**
     * Records a Battle Pass XP gain event.
     *
     * @param playerUuid identifier of the player receiving XP
     * @param xpDelta amount of XP gained (positive values)
     * @param reason optional human readable reason
     * @return future completing when the log entry is persisted
     */
    CompletableFuture<Void> insert(UUID playerUuid, int xpDelta, String reason);
}
