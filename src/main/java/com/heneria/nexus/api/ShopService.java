package com.heneria.nexus.api;

import com.heneria.nexus.config.EconomyConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/**
 * Entry point for atomic shop purchases using Nexus Coins.
 */
public interface ShopService extends LifecycleAware {

    /**
     * Attempts to purchase and unlock a class for the provided player.
     *
     * @param player player performing the purchase
     * @param classId identifier of the class to unlock
     * @return future resolving to the purchase outcome
     */
    CompletableFuture<PurchaseResult> purchaseClass(Player player, String classId);

    /**
     * Attempts to purchase and unlock a cosmetic for the provided player.
     *
     * @param player player performing the purchase
     * @param cosmeticId identifier of the cosmetic to unlock
     * @return future resolving to the purchase outcome
     */
    CompletableFuture<PurchaseResult> purchaseCosmetic(Player player, String cosmeticId);

    /**
     * Applies a refreshed shop catalog from configuration.
     *
     * @param shopSettings shop settings loaded from configuration
     */
    void applyCatalog(EconomyConfig.ShopSettings shopSettings);

    /**
     * Applies updated rate limit settings after a configuration reload.
     *
     * @param rateLimitSettings rate limiter configuration from the core config
     */
    void applyRateLimitSettings(com.heneria.nexus.config.CoreConfig.RateLimitSettings rateLimitSettings);
}
