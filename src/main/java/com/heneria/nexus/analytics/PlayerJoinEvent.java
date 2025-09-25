package com.heneria.nexus.analytics;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Analytics event emitted when a player joins the server.
 */
public record PlayerJoinEvent(UUID playerUuid,
                              String username,
                              Instant timestamp) implements AnalyticsEvent {

    public PlayerJoinEvent {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    @Override
    public String eventType() {
        return "player_join";
    }

    @Override
    public Optional<UUID> playerId() {
        return Optional.of(playerUuid);
    }

    @Override
    public Map<String, Object> data() {
        return Map.of(
                "username", username);
    }
}
