package fr.heneria.nexus.game.phase;

import fr.heneria.nexus.game.model.Match;

public interface IPhase {
    void onStart(Match match);
    void onEnd(Match match);
    void tick(Match match);
}
