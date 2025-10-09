package fr.heneria.nexus.arena;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.arena.phase.ArenaPhase;
import fr.heneria.nexus.arena.phase.ArenaScheduler;
import fr.heneria.nexus.game.cell.CelluleSystem;
import fr.heneria.nexus.game.nexus.NexusSystem;

/**
 * Représente une instance d'arène active.
 * Exemple d'utilisation du scheduler.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ArenaInstance {

    private final NexusPlugin plugin;
    private final String arenaId;
    private final ArenaScheduler scheduler;

    private final NexusSystem nexusSystem;
    private final CelluleSystem celluleSystem;

    public ArenaInstance(NexusPlugin plugin, String arenaId) {
        this.plugin = plugin;
        this.arenaId = arenaId;

        this.scheduler = plugin.getSchedulerService().createScheduler(arenaId);

        this.nexusSystem = new NexusSystem(plugin.getLogger(), arenaId);
        this.celluleSystem = new CelluleSystem(plugin.getLogger(), arenaId);

        scheduler.registerSystem(nexusSystem);
        scheduler.registerSystem(celluleSystem);

        plugin.getLogger().info("ArenaInstance créée: " + arenaId);
    }

    public void start() {
        scheduler.start(ArenaPhase.LOBBY);
        plugin.getLogger().info("[" + arenaId + "] Arène démarrée");
    }

    public void changePhase(ArenaPhase newPhase) {
        scheduler.changePhase(newPhase);
    }

    public void stop() {
        scheduler.stop();
        plugin.getSchedulerService().removeScheduler(arenaId);
        plugin.getLogger().info("[" + arenaId + "] Arène arrêtée");
    }

    public ArenaScheduler getScheduler() {
        return scheduler;
    }

    public String getArenaId() {
        return arenaId;
    }
}
