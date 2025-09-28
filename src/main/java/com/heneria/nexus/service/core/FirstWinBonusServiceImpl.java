package com.heneria.nexus.service.core;

import com.heneria.nexus.api.EconomyService;
import com.heneria.nexus.api.FirstWinBonusService;
import com.heneria.nexus.api.RewardService;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.config.EconomyConfig;
import com.heneria.nexus.util.NexusLogger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation orchestrating the daily first win bonus.
 */
public final class FirstWinBonusServiceImpl implements FirstWinBonusService {

    private static final String REWARD_KEY_PREFIX = "first_win_bonus:";
    private static final String BONUS_REASON = "Bonus première victoire";

    private final NexusLogger logger;
    private final RewardService rewardService;
    private final EconomyService economyService;
    private final AtomicReference<Settings> settings = new AtomicReference<>();

    public FirstWinBonusServiceImpl(NexusLogger logger,
                                    RewardService rewardService,
                                    EconomyService economyService,
                                    CoreConfig coreConfig,
                                    EconomyConfig economyConfig) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        applySettings(coreConfig, economyConfig);
    }

    @Override
    public CompletionStage<Boolean> grantFirstWinBonus(UUID playerId, Instant referenceInstant) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(referenceInstant, "referenceInstant");
        Settings snapshot = settings.get();
        if (snapshot == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Bonus settings not initialized"));
        }
        if (snapshot.bonusAmount() <= 0L) {
            return CompletableFuture.completedFuture(false);
        }
        LocalDate day = referenceInstant.atZone(snapshot.zoneId()).toLocalDate();
        String rewardKey = REWARD_KEY_PREFIX + day;
        long amount = snapshot.bonusAmount();
        return rewardService.claimReward(playerId, rewardKey, () -> creditBonus(playerId, amount, day))
                .thenApply(claimed -> {
                    if (claimed) {
                        logger.debug(() -> "Bonus de première victoire accordé à " + playerId + " pour " + day);
                    }
                    return claimed;
                });
    }

    @Override
    public void applySettings(CoreConfig coreConfig, EconomyConfig economyConfig) {
        Objects.requireNonNull(coreConfig, "coreConfig");
        Objects.requireNonNull(economyConfig, "economyConfig");
        ZoneId zoneId = Objects.requireNonNull(coreConfig.timezone(), "timezone");
        EconomyConfig.CoinsSettings coins = Objects.requireNonNull(economyConfig.coins(), "coins");
        long amount = coins.firstWinBonus();
        settings.set(new Settings(zoneId, amount));
        logger.debug(() -> "Configuration bonus première victoire -> zone=" + zoneId + " montant=" + amount);
    }

    private void creditBonus(UUID playerId, long amount, LocalDate day) {
        try {
            economyService.credit(playerId, amount, BONUS_REASON).toCompletableFuture().join();
        } catch (RuntimeException exception) {
            logger.error("Échec du crédit du bonus première victoire pour " + playerId + " (" + day + ")", exception);
            throw exception;
        }
    }

    private record Settings(ZoneId zoneId, long bonusAmount) {
        private Settings {
            Objects.requireNonNull(zoneId, "zoneId");
        }
    }
}
