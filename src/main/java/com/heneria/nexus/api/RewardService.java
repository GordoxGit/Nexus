package com.heneria.nexus.api;

import com.heneria.nexus.service.LifecycleAware;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Centralised entry point used to distribute unique rewards safely.
 */
public interface RewardService extends LifecycleAware {

    /**
     * Attempts to claim the provided reward for the given player.
     *
     * @param playerId unique identifier of the player receiving the reward
     * @param rewardKey stable identifier describing the reward
     * @param rewardAction side effect executed only when the reward is claimed for the first time
     * @return asynchronous stage resolving to {@code true} when the reward has been granted,
     *         or {@code false} if it was already claimed previously
     */
    CompletionStage<Boolean> claimReward(UUID playerId, String rewardKey, Runnable rewardAction);
}
