package com.heneria.nexus.api;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Provides access to player profiles with caching and degraded fallbacks.
 */
public interface ProfileService extends LifecycleAware {

    /**
     * Loads or creates the profile associated with the provided player.
     *
     * @param playerId unique identifier of the player
     * @return asynchronous stage resolving to the player profile
     */
    CompletionStage<PlayerProfile> load(UUID playerId);

    /**
     * Persists the provided profile asynchronously.
     *
     * @param profile profile to persist
     * @return asynchronous stage completing when the profile is saved
     */
    CompletionStage<Void> saveAsync(PlayerProfile profile);

    /**
     * Invalidates the cached profile for the provided player.
     *
     * @param playerId identifier of the player whose cache entry should be cleared
     */
    void invalidate(UUID playerId);

    /**
     * Applies new degraded mode settings controlling fallback behaviour.
     *
     * @param settings degraded mode configuration
     */
    void applyDegradedModeSettings(CoreConfig.DegradedModeSettings settings);

    /**
     * Returns whether the profile backend is currently operating in degraded mode.
     *
     * @return {@code true} when degraded mode is active
     */
    boolean isDegraded();
}
