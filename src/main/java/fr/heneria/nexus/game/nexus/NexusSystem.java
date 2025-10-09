package fr.heneria.nexus.game.nexus;

import fr.heneria.nexus.arena.phase.ArenaPhase;
import fr.heneria.nexus.arena.phase.Tickable;

import java.util.logging.Logger;

/**
 * Système de gestion du Nexus (objectif principal).
 * Exemple d'implémentation de Tickable.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class NexusSystem implements Tickable {

    private final Logger logger;
    private final String arenaId;

    // TODO T-058+: États du Nexus (HP, invulnérabilité, etc.)

    public NexusSystem(Logger logger, String arenaId) {
        this.logger = logger;
        this.arenaId = arenaId;
    }

    @Override
    public void tick(ArenaPhase phase, long tickCount) {
        if (tickCount % 20 == 0) {
            logger.fine("[" + arenaId + "] NexusSystem tick en phase " + phase.getDisplayName());
        }
    }

    @Override
    public boolean shouldTick(ArenaPhase phase) {
        return phase == ArenaPhase.PLAYING;
    }

    @Override
    public String getSystemName() {
        return "NexusSystem";
    }
}
