package com.heneria.nexusproxy.velocity.health;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot describing the health of a Nexus backend server.
 */
public record ServerStatusSnapshot(String serverId,
                                   int playerCount,
                                   int maxPlayers,
                                   ServerAvailability availability,
                                   double tps,
                                   Instant lastUpdate) {

    public ServerStatusSnapshot {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(lastUpdate, "lastUpdate");
        if (maxPlayers < 0) {
            throw new IllegalArgumentException("maxPlayers must be >= 0");
        }
        if (playerCount < 0) {
            throw new IllegalArgumentException("playerCount must be >= 0");
        }
    }

    public boolean canAcceptPlayers() {
        return availability.isJoinable(playerCount, maxPlayers);
    }

    public ServerStatusSnapshot markOffline(Instant now) {
        if (availability == ServerAvailability.OFFLINE) {
            return this;
        }
        return new ServerStatusSnapshot(serverId, 0, maxPlayers, ServerAvailability.OFFLINE, 0D, now);
    }
}
