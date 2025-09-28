package com.heneria.nexus.api;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.config.EconomyConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Distributes the daily first win bonus while enforcing uniqueness per player.
 */
public interface FirstWinBonusService extends LifecycleAware {

    /**
     * Attempts to grant the first win bonus for the current day.
     *
     * @param playerId unique identifier of the rewarded player
     * @return stage resolving to {@code true} when the bonus has been granted, {@code false} otherwise
     */
    default CompletionStage<Boolean> grantFirstWinBonus(UUID playerId) {
        return grantFirstWinBonus(playerId, Instant.now());
    }

    /**
     * Attempts to grant the first win bonus for the day containing the provided instant.
     *
     * @param playerId unique identifier of the rewarded player
     * @param referenceInstant instant used to determine the applicable day
     * @return stage resolving to {@code true} when the bonus has been granted, {@code false} otherwise
     */
    CompletionStage<Boolean> grantFirstWinBonus(UUID playerId, Instant referenceInstant);

    /**
     * Applies the latest configuration controlling the bonus amount and timezone.
     *
     * @param coreConfig core configuration providing the active timezone
     * @param economyConfig economy configuration providing the bonus amount
     */
    void applySettings(CoreConfig coreConfig, EconomyConfig economyConfig);
}
