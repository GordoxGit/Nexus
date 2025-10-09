package fr.heneria.nexus.game.cell;

import fr.heneria.nexus.arena.phase.ArenaPhase;
import fr.heneria.nexus.arena.phase.Tickable;

import java.util.logging.Logger;

/**
 * Système de gestion des Cellules.
 * Gère les zones de capture, les cellules actives et les porteurs.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class CelluleSystem implements Tickable {

    private final Logger logger;
    private final String arenaId;

    // TODO T-061+: Zones de capture, Cellules, Porteurs

    public CelluleSystem(Logger logger, String arenaId) {
        this.logger = logger;
        this.arenaId = arenaId;
    }

    @Override
    public void tick(ArenaPhase phase, long tickCount) {
        if (tickCount % 20 == 0) {
            logger.fine("[" + arenaId + "] CelluleSystem tick");
        }
    }

    @Override
    public boolean shouldTick(ArenaPhase phase) {
        return phase == ArenaPhase.STARTING || phase == ArenaPhase.PLAYING;
    }

    @Override
    public String getSystemName() {
        return "CelluleSystem";
    }
}
