package com.heneria.nexus.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entry representing a player waiting in the queue.
 *
 * @param playerId identifier of the queued player
 * @param mode gameplay mode requested by the player
 * @param options additional queueing options applied to the player
 * @param enqueuedAt timestamp recording when the player joined the queue
 */
public record QueueTicket(UUID playerId, ArenaMode mode, QueueOptions options, Instant enqueuedAt) {

    /**
     * Normalises optional arguments and ensures non-null components.
     */
    public QueueTicket {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mode, "mode");
        options = options == null ? QueueOptions.standard() : options;
        enqueuedAt = enqueuedAt == null ? Instant.now() : enqueuedAt;
    }
}
