package com.heneria.nexus.api;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides matchmaking capabilities for players.
 */
public interface QueueService extends LifecycleAware {

    /**
     * Adds a player to the queue.
     *
     * @param playerId identifier of the player joining the queue
     * @param mode gameplay mode requested by the player
     * @param options additional queueing options
     * @return ticket representing the queued player
     */
    QueueTicket enqueue(UUID playerId, ArenaMode mode, QueueOptions options);

    /**
     * Removes a player from the queue.
     *
     * @param playerId identifier of the player leaving the queue
     */
    void leave(UUID playerId);

    /**
     * Returns a lightweight snapshot of the queue for diagnostics.
     *
     * @return snapshot describing queued players
     */
    QueueSnapshot snapshot();

    /**
     * Returns aggregated queue metrics.
     *
     * @return statistics describing queue performance
     */
    QueueStats stats();

    /**
     * Attempts to form a match for the given mode. Invoked on the compute executor.
     *
     * @param mode gameplay mode to match
     * @return optional containing the match plan when a match is found
     */
    Optional<MatchPlan> tryMatch(ArenaMode mode);

    /**
     * Applies configuration updates impacting the queue tick rate.
     *
     * @param settings queue settings loaded from configuration
     */
    void applySettings(CoreConfig.QueueSettings settings);
}
