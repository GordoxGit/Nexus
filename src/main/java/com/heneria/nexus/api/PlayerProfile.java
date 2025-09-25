package com.heneria.nexus.api;

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

    /**
     * Creates a new profile with the provided backing data.
     *
     * @param playerId unique identifier of the player
     * @param statistics mutable map containing tracked statistics
     * @param preferences mutable map storing user preferences
     * @param cosmetics mutable list describing unlocked cosmetics
     * @param lastUpdate timestamp of the last profile update
     */
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

    /**
     * Returns the unique identifier of the player.
     *
     * @return UUID representing the player
     */
    public UUID playerId() {
        return playerId;
    }

    /**
     * Returns the mutable map of tracked statistics.
     *
     * @return statistics map keyed by statistic identifier
     */
    public Map<String, Long> statistics() {
        return statistics;
    }

    /**
     * Returns the mutable map of stored preferences.
     *
     * @return preferences map keyed by preference identifier
     */
    public Map<String, String> preferences() {
        return preferences;
    }

    /**
     * Returns the mutable list of unlocked cosmetics.
     *
     * @return list of cosmetic identifiers
     */
    public List<String> cosmetics() {
        return cosmetics;
    }

    /**
     * Returns the timestamp of the most recent update applied to this profile.
     *
     * @return last update timestamp
     */
    public Instant lastUpdate() {
        return lastUpdate;
    }

    /**
     * Updates the last update timestamp to the current instant.
     */
    public void touch() {
        this.lastUpdate = Instant.now();
    }
}
