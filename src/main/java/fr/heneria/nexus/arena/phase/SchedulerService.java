package fr.heneria.nexus.arena.phase;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.core.service.AbstractService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des schedulers d'arènes.
 * Crée et gère un scheduler par arène active.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class SchedulerService extends AbstractService {

    private final Map<String, ArenaScheduler> schedulers = new ConcurrentHashMap<>();

    public SchedulerService(NexusPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "SchedulerService";
    }

    @Override
    protected void onInitialize() throws Exception {
        logger.info("  [SchedulerService] Initialisation...");
    }

    @Override
    protected void onStart() throws Exception {
        logger.info("  [SchedulerService] Démarrage...");
    }

    @Override
    protected void onShutdown() throws Exception {
        logger.info("  [SchedulerService] Arrêt de tous les schedulers...");

        for (ArenaScheduler scheduler : schedulers.values()) {
            scheduler.stop();
        }

        int stopped = schedulers.size();
        schedulers.clear();

        logger.info("  [SchedulerService] " + stopped + " schedulers arrêtés");
    }

    /**
     * Crée un scheduler pour une arène.
     *
     * @param arenaId ID de l'arène
     * @return ArenaScheduler créé
     */
    public ArenaScheduler createScheduler(String arenaId) {
        if (schedulers.containsKey(arenaId)) {
            throw new IllegalStateException("Scheduler déjà existant pour " + arenaId);
        }

        ArenaScheduler scheduler = new ArenaScheduler(plugin, arenaId);
        schedulers.put(arenaId, scheduler);

        logger.info("Scheduler créé pour l'arène: " + arenaId);
        return scheduler;
    }

    /**
     * Récupère le scheduler d'une arène.
     *
     * @param arenaId ID de l'arène
     * @return ArenaScheduler ou null si inexistant
     */
    public ArenaScheduler getScheduler(String arenaId) {
        return schedulers.get(arenaId);
    }

    /**
     * Supprime le scheduler d'une arène.
     * Arrête le scheduler avant de le supprimer.
     *
     * @param arenaId ID de l'arène
     */
    public void removeScheduler(String arenaId) {
        ArenaScheduler scheduler = schedulers.remove(arenaId);
        if (scheduler != null) {
            scheduler.stop();
            logger.info("Scheduler supprimé pour l'arène: " + arenaId);
        }
    }

    /**
     * Retourne tous les schedulers actifs.
     *
     * @return Map immuable des schedulers
     */
    public Map<String, ArenaScheduler> getAllSchedulers() {
        return Map.copyOf(schedulers);
    }

    /**
     * Génère un rapport global de tous les schedulers.
     *
     * @return Rapport formaté
     */
    public String getGlobalReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== RAPPORT GLOBAL SCHEDULERS ===\n");
        report.append("Nombre d'arènes actives: ").append(schedulers.size()).append("\n\n");

        for (Map.Entry<String, ArenaScheduler> entry : schedulers.entrySet()) {
            report.append(entry.getValue().getMetricsReport()).append("\n\n");
        }

        return report.toString();
    }
}
