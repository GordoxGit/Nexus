package fr.heneria.nexus.core.service;

import fr.heneria.nexus.NexusPlugin;

/**
 * Service de gestion de l'économie.
 * Gère les Nexus Coins, les transactions et l'intégration Vault.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class EconomyService extends AbstractService {
    
    public EconomyService(NexusPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "EconomyService";
    }
    
    @Override
    protected void onInitialize() throws Exception {
        logger.info("  [EconomyService] Initialisation...");
        // TODO T-143+: Chargement de economy.yml, hook Vault
    }
    
    @Override
    protected void onStart() throws Exception {
        logger.info("  [EconomyService] Démarrage...");
        // TODO T-143+: Démarrage du write-behind
    }
    
    @Override
    protected void onShutdown() throws Exception {
        logger.info("  [EconomyService] Arrêt...");
        // TODO T-143+: Flush des transactions en attente
    }
}
