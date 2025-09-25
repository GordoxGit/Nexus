package com.heneria.nexus.api.service;

import com.heneria.nexus.service.LifecycleAware;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Centralized timer service managing cooldowns for arbitrary owners and keys.
 */
public interface TimerService extends LifecycleAware {

    /**
     * Applies a cooldown to an entity for the provided key.
     *
     * @param owner identifier of the cooldown owner (player, entity, etc.)
     * @param key unique identifier for the cooldown
     * @param durationMillis duration of the cooldown in milliseconds
     */
    void setCooldown(UUID owner, String key, long durationMillis);

    /**
     * Checks if a cooldown is currently active for the provided owner and key.
     *
     * @param owner identifier of the cooldown owner
     * @param key unique identifier for the cooldown
     * @return {@code true} when the cooldown is still active
     */
    boolean isOnCooldown(UUID owner, String key);

    /**
     * Retrieves the remaining time in milliseconds for an active cooldown.
     *
     * @param owner identifier of the cooldown owner
     * @param key unique identifier for the cooldown
     * @return optional containing the remaining duration or empty when not on cooldown
     */
    OptionalLong getRemainingMillis(UUID owner, String key);

    /**
     * Clears any active cooldown associated with the owner and key.
     *
     * @param owner identifier of the cooldown owner
     * @param key unique identifier for the cooldown
     */
    void clearCooldown(UUID owner, String key);
}
