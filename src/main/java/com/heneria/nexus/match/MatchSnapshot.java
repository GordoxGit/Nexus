package com.heneria.nexus.match;

import com.heneria.nexus.api.ArenaMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable snapshot describing the outcome of a played match.
 */
public record MatchSnapshot(UUID matchId,
                            String mapId,
                            ArenaMode mode,
                            Instant startTime,
                            Instant endTime,
                            Optional<String> winningTeam,
                            List<ParticipantStats> participants) {

    public MatchSnapshot {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(winningTeam, "winningTeam");
        Objects.requireNonNull(participants, "participants");
    }

    public record ParticipantStats(UUID playerId,
                                   String team,
                                   int kills,
                                   int deaths,
                                   int eloChange) {

        public ParticipantStats {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(team, "team");
        }
    }
}
