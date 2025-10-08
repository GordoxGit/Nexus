package fr.heneria.nexus.core.service;

import fr.heneria.nexus.NexusPlugin;

/**
 * Service de gestion des profils joueurs.
 * Gère le chargement, la sauvegarde et le cache des données joueurs.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ProfileService extends AbstractService {
    
    public ProfileService(NexusPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "ProfileService";
    }
    
    @Override
    protected void onInitialize() throws Exception {
        logger.info("  [ProfileService] Initialisation...");
        // TODO T-024: Setup du cache LRU
    }
    
    @Override
    protected void onStart() throws Exception {
        logger.info("  [ProfileService] Démarrage...");
        // TODO T-024: Enregistrement des listeners
    }
    
    @Override
    protected void onShutdown() throws Exception {
        logger.info("  [ProfileService] Arrêt...");
        // TODO T-024: Flush tous les profils en cache
    }
}
