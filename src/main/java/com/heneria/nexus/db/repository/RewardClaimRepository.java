package com.heneria.nexus.db.repository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository tracking claimed one-time rewards to guarantee idempotence.
 */
public interface RewardClaimRepository {

    /**
     * Attempts to record a reward claim for the provided player.
     *
     * @param playerUuid unique identifier of the player claiming the reward
     * @param rewardKey unique key identifying the reward
     * @return future resolving to {@code true} when the reward was claimed for the first time
     *         or {@code false} when the reward was already recorded
     */
    CompletableFuture<Boolean> tryClaim(UUID playerUuid, String rewardKey);
}
