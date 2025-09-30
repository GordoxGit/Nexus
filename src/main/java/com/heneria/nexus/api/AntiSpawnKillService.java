package com.heneria.nexus.api;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Provides temporary invulnerability to players when they spawn in an arena.
 */
public interface AntiSpawnKillService extends LifecycleAware {

    /**
     * Applies the configured spawn protection to the supplied player.
     *
     * @param player player receiving the protection
     */
    void applyProtection(Player player);

    /**
     * Revokes the spawn protection applied to the player, if present.
     *
     * @param playerId unique identifier of the player
     */
    void revokeProtection(UUID playerId);

    /**
     * Convenience overload accepting a player instance.
     *
     * @param player player whose protection should be revoked
     */
    default void revokeProtection(Player player) {
        Objects.requireNonNull(player, "player");
        revokeProtection(player.getUniqueId());
    }

    /**
     * Indicates whether the player is currently protected.
     *
     * @param playerId unique identifier of the player
     * @return {@code true} when the player still benefits from the protection
     */
    boolean isProtected(UUID playerId);

    /**
     * Updates the runtime configuration of the service.
     *
     * @param settings spawn protection settings loaded from the configuration file
     */
    void applySettings(CoreConfig.ArenaSettings.SpawnProtectionSettings settings);
}
