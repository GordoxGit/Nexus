package com.heneria.nexus.analytics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Analytics event captured when an arena reaches its end state.
 */
public record MatchCompletedEvent(UUID arenaId,
                                  String mapId,
                                  String mode,
                                  Instant startedAt,
                                  Instant timestamp) implements AnalyticsEvent {

    public MatchCompletedEvent {
        Objects.requireNonNull(arenaId, "arenaId");
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    @Override
    public String eventType() {
        return "match_completed";
    }

    @Override
    public Optional<UUID> playerId() {
        return Optional.empty();
    }

    @Override
    public Map<String, Object> data() {
        long durationMillis = Math.max(0L, Duration.between(startedAt, timestamp).toMillis());
        return Map.of(
                "arenaId", arenaId.toString(),
                "mapId", mapId,
                "mode", mode,
                "startedAt", startedAt.toString(),
                "durationMillis", durationMillis);
    }
}
