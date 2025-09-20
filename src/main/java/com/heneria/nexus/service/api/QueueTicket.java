package com.heneria.nexus.service.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entry representing a player waiting in the queue.
 */
public record QueueTicket(UUID playerId, ArenaMode mode, QueueOptions options, Instant enqueuedAt) {

    public QueueTicket {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mode, "mode");
        options = options == null ? QueueOptions.standard() : options;
        enqueuedAt = enqueuedAt == null ? Instant.now() : enqueuedAt;
    }
}
