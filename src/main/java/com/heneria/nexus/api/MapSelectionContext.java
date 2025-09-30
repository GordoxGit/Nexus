package com.heneria.nexus.api;

import java.util.List;
import java.util.Objects;

/**
 * Describes the constraints that must be satisfied when selecting map candidates.
 *
 * @param mode gameplay mode requested for the match
 * @param playerCount number of players that will participate in the match
 * @param recentlyPlayed list of map identifiers that should be excluded from the rotation
 */
public record MapSelectionContext(ArenaMode mode, int playerCount, List<String> recentlyPlayed) {

    /**
     * Validates constructor arguments and normalises optional fields.
     */
    public MapSelectionContext {
        Objects.requireNonNull(mode, "mode");
        if (playerCount < 0) {
            throw new IllegalArgumentException("playerCount must be >= 0");
        }
        recentlyPlayed = recentlyPlayed == null ? List.of() : List.copyOf(recentlyPlayed);
    }
}
