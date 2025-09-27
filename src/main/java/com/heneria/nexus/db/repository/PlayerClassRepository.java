package com.heneria.nexus.db.repository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository dedicated to player class ownership persistence.
 */
public interface PlayerClassRepository {

    /**
     * Checks whether the provided player has unlocked the given class.
     *
     * @param playerUuid unique identifier of the player
     * @param classId identifier of the class to check
     * @return future yielding {@code true} when the class is unlocked
     */
    CompletableFuture<Boolean> isUnlocked(UUID playerUuid, String classId);

    /**
     * Unlocks the provided class for the given player.
     *
     * @param playerUuid unique identifier of the player
     * @param classId identifier of the class to unlock
     * @return future completed once the unlock has been persisted
     */
    CompletableFuture<Void> unlock(UUID playerUuid, String classId);
}
