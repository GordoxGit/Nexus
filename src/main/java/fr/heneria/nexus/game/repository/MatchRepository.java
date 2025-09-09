package fr.heneria.nexus.game.repository;

import fr.heneria.nexus.game.model.Match;

public interface MatchRepository {
    void saveMatchResult(Match match, int winningTeamId);
    void saveMatchParticipants(Match match);
}
