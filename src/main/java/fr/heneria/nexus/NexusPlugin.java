package fr.heneria.nexus;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal Nexus - Mode de jeu PvP compétitif par équipes
 *
 * @author GordoxGit
 * @version 1.0.0-ALPHA
 * @since 1.21
 */
public final class NexusPlugin extends JavaPlugin {

    private static NexusPlugin instance;

    @Override
    public void onLoad() {
        instance = this;
        getLogger().info("========================================");
        getLogger().info("  NEXUS - Chargement...");
        getLogger().info("  Version: " + getDescription().getVersion());
        getLogger().info("  Java: " + System.getProperty("java.version"));
        getLogger().info("========================================");
    }

    @Override
    public void onEnable() {
        getLogger().info("Démarrage du plugin Nexus...");

        // TODO: Phase 1 - Initialisation des services
        // TODO: Phase 1 - Chargement de la configuration
        // TODO: Phase 1 - Connexion à la base de données
        // TODO: Phase 1 - Enregistrement des commandes
        // TODO: Phase 1 - Enregistrement des listeners

        getLogger().info("Nexus activé avec succès !");
    }

    @Override
    public void onDisable() {
        getLogger().info("Arrêt du plugin Nexus...");

        // TODO: Phase 1 - Shutdown des services
        // TODO: Phase 1 - Fermeture des connexions DB
        // TODO: Phase 1 - Sauvegarde des données en attente

        getLogger().info("Nexus désactivé.");
    }

    /**
     * Récupère l'instance du plugin
     *
     * @return Instance singleton de NexusPlugin
     */
    public static NexusPlugin getInstance() {
        return instance;
    }
}
