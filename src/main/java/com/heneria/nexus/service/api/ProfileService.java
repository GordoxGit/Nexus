package com.heneria.nexus.service.api;

import com.heneria.nexus.config.NexusConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Provides access to player profiles with caching and degraded fallbacks.
 */
public interface ProfileService extends LifecycleAware {

    CompletionStage<PlayerProfile> load(UUID playerId);

    CompletionStage<Void> saveAsync(PlayerProfile profile);

    void invalidate(UUID playerId);

    void applyDegradedModeSettings(NexusConfig.DegradedModeSettings settings);

    boolean isDegraded();
}
