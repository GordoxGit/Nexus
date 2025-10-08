package fr.heneria.nexus.core.service;

import fr.heneria.nexus.NexusPlugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Classe abstraite de base pour simplifier l'implémentation des services.
 * Fournit des utilitaires communs et gère l'état running automatiquement.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public abstract class AbstractService implements Service {
    
    protected final NexusPlugin plugin;
    protected final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /**
     * Constructeur de base
     *
     * @param plugin Instance du plugin principal
     */
    protected AbstractService(NexusPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    @Override
    public void initialize() throws ServiceException {
        if (running.get()) {
            throw new ServiceException(getName(), ServiceException.Phase.INITIALIZATION,
                "Service déjà initialisé");
        }
        
        try {
            onInitialize();
        } catch (Exception e) {
            throw new ServiceException(getName(), ServiceException.Phase.INITIALIZATION,
                "Erreur lors de l'initialisation", e);
        }
    }
    
    @Override
    public void start() throws ServiceException {
        if (running.get()) {
            throw new ServiceException(getName(), ServiceException.Phase.STARTUP,
                "Service déjà démarré");
        }
        
        try {
            onStart();
            running.set(true);
        } catch (Exception e) {
            throw new ServiceException(getName(), ServiceException.Phase.STARTUP,
                "Erreur lors du démarrage", e);
        }
    }
    
    @Override
    public void shutdown() throws ServiceException {
        if (!running.get()) {
            logger.warning(getName() + " : Tentative d'arrêt d'un service non démarré");
            return;
        }
        
        try {
            onShutdown();
            running.set(false);
        } catch (Exception e) {
            throw new ServiceException(getName(), ServiceException.Phase.SHUTDOWN,
                "Erreur lors de l'arrêt", e);
        }
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Méthode appelée lors de l'initialisation.
     * À implémenter par les classes filles.
     *
     * @throws Exception si une erreur survient
     */
    protected abstract void onInitialize() throws Exception;
    
    /**
     * Méthode appelée lors du démarrage.
     * À implémenter par les classes filles.
     *
     * @throws Exception si une erreur survient
     */
    protected abstract void onStart() throws Exception;
    
    /**
     * Méthode appelée lors de l'arrêt.
     * À implémenter par les classes filles.
     *
     * @throws Exception si une erreur survient
     */
    protected abstract void onShutdown() throws Exception;
}
