package com.heneria.nexus.service.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Mutable view representing a player profile.
 */
public final class PlayerProfile {

    private final UUID playerId;
    private final Map<String, Long> statistics;
    private final Map<String, String> preferences;
    private final List<String> cosmetics;
    private Instant lastUpdate;

    public PlayerProfile(UUID playerId,
                         Map<String, Long> statistics,
                         Map<String, String> preferences,
                         List<String> cosmetics,
                         Instant lastUpdate) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.cosmetics = Objects.requireNonNull(cosmetics, "cosmetics");
        this.lastUpdate = Objects.requireNonNullElse(lastUpdate, Instant.now());
    }

    public UUID playerId() {
        return playerId;
    }

    public Map<String, Long> statistics() {
        return statistics;
    }

    public Map<String, String> preferences() {
        return preferences;
    }

    public List<String> cosmetics() {
        return cosmetics;
    }

    public Instant lastUpdate() {
        return lastUpdate;
    }

    public void touch() {
        this.lastUpdate = Instant.now();
    }
}
