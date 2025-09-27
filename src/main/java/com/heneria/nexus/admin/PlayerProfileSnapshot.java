package com.heneria.nexus.admin;

import com.heneria.nexus.api.PlayerProfile;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of the statistics stored for a player profile.
 */
public record PlayerProfileSnapshot(long eloRating,
                                    long totalKills,
                                    long totalDeaths,
                                    long totalWins,
                                    long totalLosses,
                                    long matchesPlayed) {

    private static final long DEFAULT_ELO = 1000L;

    /**
     * Creates an empty snapshot with default values.
     *
     * @return snapshot populated with default statistics
     */
    public static PlayerProfileSnapshot empty() {
        return new PlayerProfileSnapshot(DEFAULT_ELO, 0L, 0L, 0L, 0L, 0L);
    }

    /**
     * Extracts a snapshot from the provided profile instance.
     *
     * @param profile mutable profile backing the data
     * @return immutable snapshot view
     */
    public static PlayerProfileSnapshot fromProfile(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        Map<String, Long> statistics = profile.statistics();
        return new PlayerProfileSnapshot(
                statistics.getOrDefault("elo_rating", DEFAULT_ELO),
                statistics.getOrDefault("total_kills", 0L),
                statistics.getOrDefault("total_deaths", 0L),
                statistics.getOrDefault("total_wins", 0L),
                statistics.getOrDefault("total_losses", 0L),
                statistics.getOrDefault("matches_played", 0L));
    }
}
