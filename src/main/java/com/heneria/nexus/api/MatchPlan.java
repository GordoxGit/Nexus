package com.heneria.nexus.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Result of a matchmaking iteration.
 *
 * @param matchId identifier assigned to the potential match
 * @param mode gameplay mode that was matched
 * @param players participating players selected for the match
 * @param mapCandidate optional preferred map identifier
 */
public record MatchPlan(UUID matchId, ArenaMode mode, List<UUID> players, Optional<String> mapCandidate) {

    /**
     * Normalises optional fields and copies mutable collections.
     */
    public MatchPlan {
        players = List.copyOf(players);
        mapCandidate = mapCandidate == null ? Optional.empty() : mapCandidate;
    }
}
