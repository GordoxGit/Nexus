package com.heneria.nexus.service.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Result of a matchmaking iteration.
 */
public record MatchPlan(UUID matchId, ArenaMode mode, List<UUID> players, Optional<String> mapCandidate) {

    public MatchPlan {
        players = List.copyOf(players);
        mapCandidate = mapCandidate == null ? Optional.empty() : mapCandidate;
    }
}
