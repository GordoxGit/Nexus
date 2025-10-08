package fr.heneria.nexus.core.service;

import fr.heneria.nexus.NexusPlugin;

/**
 * Service de gestion des maps.
 * Charge, valide et gère le catalogue des maps disponibles.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class MapService extends AbstractService {
    
    public MapService(NexusPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "MapService";
    }
    
    @Override
    protected void onInitialize() throws Exception {
        logger.info("  [MapService] Initialisation...");
        // TODO T-021: Chargement du fichier maps.yml
    }
    
    @Override
    protected void onStart() throws Exception {
        logger.info("  [MapService] Démarrage...");
        // TODO T-021: Validation et indexation des maps
    }
    
    @Override
    protected void onShutdown() throws Exception {
        logger.info("  [MapService] Arrêt...");
        // TODO T-021: Cleanup cache
    }
}
