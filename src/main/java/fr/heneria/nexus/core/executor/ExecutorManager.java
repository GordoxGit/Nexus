package fr.heneria.nexus.core.executor;

import fr.heneria.nexus.NexusPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Gestionnaire des pools d'executors du plugin.
 * Crée, gère et shutdown proprement les thread pools.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ExecutorManager {

    private final NexusPlugin plugin;
    private final Logger logger;

    private ExecutorService ioPool;
    private ExecutorService computePool;
    private final List<ExecutorService> allPools = new ArrayList<>();

    private boolean initialized = false;

    /**
     * Constructeur
     *
     * @param plugin Instance du plugin
     */
    public ExecutorManager(NexusPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Initialise les pools avec les configurations par défaut.
     */
    public void initialize() {
        initialize(PoolConfig.ioDefault(), PoolConfig.computeDefault());
    }

    /**
     * Initialise les pools avec des configurations personnalisées.
     *
     * @param ioConfig Configuration du pool I/O
     * @param computeConfig Configuration du pool Compute
     */
    public void initialize(PoolConfig ioConfig, PoolConfig computeConfig) {
        if (initialized) {
            throw new IllegalStateException("ExecutorManager déjà initialisé");
        }

        logger.info("Initialisation des pools d'executors...");

        allPools.clear();

        // Créer le pool I/O
        ioPool = createPool(ioConfig);
        allPools.add(ioPool);
        logger.info("✓ Pool I/O créé : " + formatPoolInfo(ioConfig));

        // Créer le pool Compute
        computePool = createPool(computeConfig);
        allPools.add(computePool);
        logger.info("✓ Pool Compute créé : " + formatPoolInfo(computeConfig));

        initialized = true;
        logger.info("✓ ExecutorManager initialisé");
    }

    /**
     * Crée un ThreadPoolExecutor selon la configuration.
     *
     * @param config Configuration du pool
     * @return ExecutorService configuré
     */
    private ExecutorService createPool(PoolConfig config) {
        BlockingQueue<Runnable> queue;

        if (config.queueSize() == 0) {
            queue = new LinkedBlockingQueue<>();
        } else {
            queue = new ArrayBlockingQueue<>(config.queueSize());
        }

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            config.coreSize(),
            config.maxSize(),
            config.keepAliveSeconds(),
            TimeUnit.SECONDS,
            queue,
            new NexusThreadFactory(config.name()),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.allowCoreThreadTimeOut(true);

        return executor;
    }

    /**
     * Formate les infos d'un pool pour le log.
     *
     * @param config Configuration
     * @return String formaté
     */
    private String formatPoolInfo(PoolConfig config) {
        return String.format("%s [core:%d, max:%d, queue:%d, keepAlive:%ds]",
            config.name(),
            config.coreSize(),
            config.maxSize(),
            config.queueSize(),
            config.keepAliveSeconds()
        );
    }

    /**
     * Retourne le pool I/O (base de données, fichiers, schémas).
     *
     * @return ExecutorService I/O
     */
    public ExecutorService getIoPool() {
        checkInitialized();
        return ioPool;
    }

    /**
     * Retourne le pool Compute (calculs lourds, A*, agrégations).
     *
     * @return ExecutorService Compute
     */
    public ExecutorService getComputePool() {
        checkInitialized();
        return computePool;
    }

    /**
     * Vérifie que le manager est initialisé.
     *
     * @throws IllegalStateException si non initialisé
     */
    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("ExecutorManager non initialisé");
        }
    }

    /**
     * Arrête tous les pools proprement avec timeout.
     * Tente un shutdown gracieux, puis force si nécessaire.
     *
     * @param timeoutSeconds Timeout en secondes
     */
    public void shutdown(int timeoutSeconds) {
        if (!initialized) {
            logger.warning("ExecutorManager non initialisé, shutdown ignoré");
            return;
        }

        logger.info("========================================");
        logger.info("Arrêt des pools d'executors...");
        logger.info("========================================");

        long startTime = System.currentTimeMillis();

        for (ExecutorService pool : allPools) {
            pool.shutdown();
        }

        logger.info("→ Attente de la fin des tâches en cours (" + timeoutSeconds + "s max)...");

        boolean allTerminated = true;
        for (ExecutorService pool : allPools) {
            try {
                if (!pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                    logger.warning("⚠ Pool n'a pas terminé dans les temps, forçage...");
                    List<Runnable> pending = pool.shutdownNow();
                    logger.warning("  " + pending.size() + " tâches en attente annulées");
                    allTerminated = false;
                }
            } catch (InterruptedException e) {
                logger.warning("⚠ Interruption durant le shutdown, forçage...");
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                allTerminated = false;
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        logger.info("========================================");
        if (allTerminated) {
            logger.info("✓ Tous les pools arrêtés proprement en " + duration + "ms");
        } else {
            logger.warning("⚠ Certains pools ont nécessité un arrêt forcé (" + duration + "ms)");
        }
        logger.info("========================================");

        initialized = false;
        allPools.clear();
    }

    /**
     * Retourne des statistiques sur les pools.
     *
     * @return Rapport de status
     */
    public String getStatusReport() {
        if (!initialized) {
            return "ExecutorManager non initialisé";
        }

        StringBuilder report = new StringBuilder();
        report.append("=== EXECUTOR POOLS STATUS ===\n");

        report.append("I/O Pool:\n");
        report.append(getPoolStats((ThreadPoolExecutor) ioPool));

        report.append("\nCompute Pool:\n");
        report.append(getPoolStats((ThreadPoolExecutor) computePool));

        report.append("=============================");
        return report.toString();
    }

    /**
     * Récupère les stats d'un pool.
     *
     * @param pool ThreadPoolExecutor
     * @return Stats formatées
     */
    private String getPoolStats(ThreadPoolExecutor pool) {
        return String.format(
            "  Threads actifs: %d/%d\n" +
            "  Tâches complétées: %d\n" +
            "  Tâches en queue: %d\n" +
            "  Pool size: %d (core: %d, max: %d)",
            pool.getActiveCount(),
            pool.getPoolSize(),
            pool.getCompletedTaskCount(),
            pool.getQueue().size(),
            pool.getPoolSize(),
            pool.getCorePoolSize(),
            pool.getMaximumPoolSize()
        );
    }

    /**
     * ThreadFactory personnalisée pour nommer les threads.
     */
    private static class NexusThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private int counter = 0;

        NexusThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(namePrefix + "-" + counter++);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> {
                NexusPlugin.getInstance().getLogger().severe(
                    "Exception non catchée dans le thread " + t.getName() + ": " + e.getMessage()
                );
                e.printStackTrace();
            });
            return thread;
        }
    }
}
