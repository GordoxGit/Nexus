package fr.heneria.nexus.core.service;

import fr.heneria.nexus.NexusPlugin;

/**
 * Service de gestion des files d'attente.
 * Gère le matchmaking, les queues normales/classées et la composition des équipes.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class QueueService extends AbstractService {
    
    public QueueService(NexusPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "QueueService";
    }
    
    @Override
    protected void onInitialize() throws Exception {
        logger.info("  [QueueService] Initialisation...");
        // TODO T-108+: Création des files normales/classées
    }
    
    @Override
    protected void onStart() throws Exception {
        logger.info("  [QueueService] Démarrage...");
        // TODO T-108+: Démarrage du scheduler de matchmaking
    }
    
    @Override
    protected void onShutdown() throws Exception {
        logger.info("  [QueueService] Arrêt...");
        // TODO T-108+: Éjecter les joueurs en queue
    }
}
