package com.heneria.nexus.analytics;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Analytics event emitted when a player joins the server.
 */
public record PlayerJoinEvent(UUID playerId,
                              String username,
                              Instant timestamp) implements AnalyticsEvent {

    public PlayerJoinEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    @Override
    public String eventType() {
        return "player_join";
    }

    @Override
    public Optional<UUID> playerId() {
        return Optional.of(playerId);
    }

    @Override
    public Map<String, Object> data() {
        return Map.of(
                "username", username);
    }
}
