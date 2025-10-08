package fr.heneria.nexus.core.service;

import fr.heneria.nexus.NexusPlugin;

/**
 * Service de gestion des arènes.
 * Gère les instances d'arènes, leur cycle de vie et leur état.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ArenaService extends AbstractService {
    
    public ArenaService(NexusPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "ArenaService";
    }
    
    @Override
    protected void onInitialize() throws Exception {
        logger.info("  [ArenaService] Initialisation...");
        // TODO T-021+: Chargement des maps, création du pool d'arènes
    }
    
    @Override
    protected void onStart() throws Exception {
        logger.info("  [ArenaService] Démarrage...");
        // TODO T-021+: Démarrage des schedulers d'arènes
    }
    
    @Override
    protected void onShutdown() throws Exception {
        logger.info("  [ArenaService] Arrêt...");
        // TODO T-021+: Sauvegarde des états, cleanup des arènes
    }
}
