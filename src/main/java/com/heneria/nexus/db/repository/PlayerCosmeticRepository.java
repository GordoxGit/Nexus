package com.heneria.nexus.db.repository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository dedicated to player cosmetic ownership persistence.
 */
public interface PlayerCosmeticRepository {

    /**
     * Checks whether the provided player has unlocked the given cosmetic.
     *
     * @param playerUuid unique identifier of the player
     * @param cosmeticId identifier of the cosmetic to check
     * @return future yielding {@code true} when the cosmetic is unlocked
     */
    CompletableFuture<Boolean> isUnlocked(UUID playerUuid, String cosmeticId);

    /**
     * Unlocks the provided cosmetic for the given player.
     *
     * @param playerUuid unique identifier of the player
     * @param cosmeticId identifier of the cosmetic to unlock
     * @param cosmeticType cosmetic type used for auditing
     * @return future completed once the unlock has been persisted
     */
    CompletableFuture<Void> unlock(UUID playerUuid, String cosmeticId, String cosmeticType);
}
