package fr.heneria.nexus.arena.phase;

import fr.heneria.nexus.NexusPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Scheduler pour une arène individuelle.
 * Gère les ticks des différents systèmes selon la phase active.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ArenaScheduler {

    private final NexusPlugin plugin;
    private final Logger logger;
    private final String arenaId;

    private final List<Tickable> systems = new ArrayList<>();
    private final Map<String, TickMetrics> metricsMap = new ConcurrentHashMap<>();

    private ArenaPhase currentPhase = ArenaPhase.LOBBY;
    private BukkitTask currentTask;
    private long phaseStartTime;
    private long tickCount = 0;

    private boolean running = false;

    /**
     * Constructeur
     *
     * @param plugin  Instance du plugin
     * @param arenaId ID de l'arène
     */
    public ArenaScheduler(NexusPlugin plugin, String arenaId) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.arenaId = arenaId;
    }

    /**
     * Enregistre un système tickable.
     *
     * @param system Système à enregistrer
     */
    public void registerSystem(Tickable system) {
        systems.add(system);
        metricsMap.put(system.getSystemName(), new TickMetrics());
        logger.fine("Système '" + system.getSystemName() + "' enregistré pour " + arenaId);
    }

    /**
     * Démarre le scheduler dans une phase donnée.
     *
     * @param initialPhase Phase initiale
     */
    public void start(ArenaPhase initialPhase) {
        if (running) {
            logger.warning("ArenaScheduler déjà démarré pour " + arenaId);
            return;
        }

        this.currentPhase = initialPhase;
        this.phaseStartTime = System.currentTimeMillis();
        this.tickCount = 0;
        this.running = true;

        scheduleNextTick();

        logger.info("[" + arenaId + "] Scheduler démarré en phase " + currentPhase.getDisplayName()
            + " (" + currentPhase.getTickRate() + " Hz)");
    }

    /**
     * Change la phase et ajuste la fréquence de tick.
     *
     * @param newPhase Nouvelle phase
     */
    public void changePhase(ArenaPhase newPhase) {
        if (newPhase == currentPhase) {
            return;
        }

        ArenaPhase oldPhase = currentPhase;
        this.currentPhase = newPhase;
        this.phaseStartTime = System.currentTimeMillis();
        this.tickCount = 0;

        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel();
        }

        if (running) {
            scheduleNextTick();
        }

        logger.info("[" + arenaId + "] Changement de phase: " + oldPhase.getDisplayName()
            + " → " + newPhase.getDisplayName() + " (" + newPhase.getTickRate() + " Hz)");
    }

    /**
     * Schedule le prochain tick selon la fréquence de la phase.
     */
    private void scheduleNextTick() {
        long interval = currentPhase.getTickInterval();

        currentTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::executeTick,
            interval,
            interval
        );
    }

    /**
     * Exécute un tick : appelle tous les systèmes actifs.
     */
    private void executeTick() {
        long startNano = System.nanoTime();
        tickCount++;

        int activeSystems = 0;

        for (Tickable system : systems) {
            if (!system.shouldTick(currentPhase)) {
                continue;
            }

            activeSystems++;

            long systemStartNano = System.nanoTime();

            try {
                system.tick(currentPhase, tickCount);
            } catch (Exception e) {
                logger.severe("[" + arenaId + "] Erreur dans le système '"
                    + system.getSystemName() + "': " + e.getMessage());
                e.printStackTrace();
            }

            long systemDurationNano = System.nanoTime() - systemStartNano;

            TickMetrics metrics = metricsMap.get(system.getSystemName());
            if (metrics != null) {
                metrics.recordTick(systemDurationNano);
            }

            double systemMspt = systemDurationNano / 1_000_000.0;
            int budget = currentPhase.getRecommendedMsptBudget();

            if (systemMspt > budget) {
                logger.warning(String.format(
                    "[%s] Système '%s' a dépassé son budget: %.2fms > %dms",
                    arenaId, system.getSystemName(), systemMspt, budget
                ));
            }
        }

        long totalDurationNano = System.nanoTime() - startNano;
        double totalMspt = totalDurationNano / 1_000_000.0;

        if (totalMspt > 10.0) {
            logger.warning(String.format(
                "[%s] Tick lent: %.2fms (%d systèmes actifs)",
                arenaId, totalMspt, activeSystems
            ));
        }
    }

    /**
     * Arrête le scheduler.
     */
    public void stop() {
        if (!running) {
            return;
        }

        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel();
        }

        running = false;

        logger.info("[" + arenaId + "] Scheduler arrêté");
    }

    /**
     * Retourne la phase actuelle.
     *
     * @return Phase actuelle
     */
    public ArenaPhase getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Retourne le temps passé dans la phase actuelle (ms).
     *
     * @return Durée en millisecondes
     */
    public long getPhaseElapsedTime() {
        return System.currentTimeMillis() - phaseStartTime;
    }

    /**
     * Retourne le nombre de ticks depuis le début de la phase.
     *
     * @return Compteur de ticks
     */
    public long getTickCount() {
        return tickCount;
    }

    /**
     * Indique si le scheduler est actif.
     *
     * @return true si actif
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Génère un rapport de métriques des systèmes.
     *
     * @return Rapport formaté
     */
    public String getMetricsReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== MÉTRIQUES SCHEDULER - ").append(arenaId).append(" ===\n");
        report.append("Phase: ").append(currentPhase.getDisplayName()).append(" (")
            .append(currentPhase.getTickRate()).append(" Hz)\n");
        report.append("Temps dans phase: ").append(getPhaseElapsedTime()).append("ms\n");
        report.append("Ticks: ").append(tickCount).append("\n\n");

        report.append("Systèmes:\n");
        for (Tickable system : systems) {
            TickMetrics metrics = metricsMap.get(system.getSystemName());
            if (metrics != null) {
                report.append("  - ").append(system.getSystemName()).append(":\n");
                report.append("    Ticks: ").append(metrics.getTotalTicks()).append("\n");
                report.append("    Moyenne: ").append(String.format("%.3f", metrics.getAverageMspt()))
                    .append("ms\n");
                report.append("    Max: ").append(String.format("%.3f", metrics.getMaxMspt()))
                    .append("ms\n");
            }
        }

        report.append("==========================================");
        return report.toString();
    }

    /**
     * Classe interne pour stocker les métriques d'un système.
     */
    private static class TickMetrics {
        private long totalTicks = 0;
        private long totalNano = 0;
        private long maxNano = 0;

        void recordTick(long durationNano) {
            totalTicks++;
            totalNano += durationNano;
            if (durationNano > maxNano) {
                maxNano = durationNano;
            }
        }

        long getTotalTicks() {
            return totalTicks;
        }

        double getAverageMspt() {
            if (totalTicks == 0) {
                return 0.0;
            }
            return (totalNano / (double) totalTicks) / 1_000_000.0;
        }

        double getMaxMspt() {
            return maxNano / 1_000_000.0;
        }
    }
}
