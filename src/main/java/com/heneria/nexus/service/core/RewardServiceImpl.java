package com.heneria.nexus.service.core;

import com.heneria.nexus.api.RewardService;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.repository.RewardClaimRepository;
import com.heneria.nexus.util.NexusLogger;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Default implementation ensuring unique rewards are granted at most once.
 */
public final class RewardServiceImpl implements RewardService {

    private final NexusLogger logger;
    private final RewardClaimRepository rewardClaimRepository;
    private final Executor ioExecutor;

    public RewardServiceImpl(NexusLogger logger,
                             RewardClaimRepository rewardClaimRepository,
                             ExecutorManager executorManager) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.rewardClaimRepository = Objects.requireNonNull(rewardClaimRepository, "rewardClaimRepository");
        this.ioExecutor = Objects.requireNonNull(executorManager, "executorManager").io();
    }

    @Override
    public CompletionStage<Boolean> claimReward(UUID playerId, String rewardKey, Runnable rewardAction) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardKey, "rewardKey");
        Objects.requireNonNull(rewardAction, "rewardAction");
        String normalizedKey = rewardKey.trim();
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("rewardKey must not be blank");
        }
        return rewardClaimRepository.tryClaim(playerId, normalizedKey)
                .thenApplyAsync(claimed -> {
                    if (!claimed) {
                        return false;
                    }
                    try {
                        rewardAction.run();
                        return true;
                    } catch (Throwable throwable) {
                        logger.error("Échec de l'exécution de la récompense %s pour %s".formatted(normalizedKey, playerId),
                                throwable);
                        throw new CompletionException(throwable);
                    }
                }, ioExecutor);
    }
}
