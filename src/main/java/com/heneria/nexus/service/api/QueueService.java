package com.heneria.nexus.service.api;

import com.heneria.nexus.config.NexusConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides matchmaking capabilities for players.
 */
public interface QueueService extends LifecycleAware {

    /**
     * Adds a player to the queue.
     */
    QueueTicket enqueue(UUID playerId, ArenaMode mode, QueueOptions options);

    /**
     * Removes a player from the queue.
     */
    void leave(UUID playerId);

    /**
     * Returns a lightweight snapshot of the queue for diagnostics.
     */
    QueueSnapshot snapshot();

    /**
     * Returns aggregated queue metrics.
     */
    QueueStats stats();

    /**
     * Attempts to form a match for the given mode. Invoked on the compute executor.
     */
    Optional<MatchPlan> tryMatch(ArenaMode mode);

    /**
     * Applies configuration updates impacting the queue tick rate.
     */
    void applySettings(NexusConfig.QueueSettings settings);
}
