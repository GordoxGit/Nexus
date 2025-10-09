package fr.heneria.nexus.core.executor;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.core.service.AbstractService;

/**
 * Service de gestion des executors.
 * Wraps ExecutorManager dans le système de services.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ExecutorService extends AbstractService {

    private ExecutorManager executorManager;

    public ExecutorService(NexusPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "ExecutorService";
    }

    @Override
    protected void onInitialize() throws Exception {
        executorManager = new ExecutorManager(plugin);
        executorManager.initialize();
    }

    @Override
    protected void onStart() throws Exception {
        logger.info("✓ Pools d'executors opérationnels");
    }

    @Override
    protected void onShutdown() throws Exception {
        if (executorManager != null) {
            executorManager.shutdown(10);
        }
    }

    /**
     * Retourne le gestionnaire d'executors.
     *
     * @return ExecutorManager
     */
    public ExecutorManager getExecutorManager() {
        return executorManager;
    }

    /**
     * Raccourci pour obtenir le pool I/O.
     *
     * @return I/O ExecutorService
     */
    public java.util.concurrent.ExecutorService getIoPool() {
        return executorManager.getIoPool();
    }

    /**
     * Raccourci pour obtenir le pool Compute.
     *
     * @return Compute ExecutorService
     */
    public java.util.concurrent.ExecutorService getComputePool() {
        return executorManager.getComputePool();
    }
}
