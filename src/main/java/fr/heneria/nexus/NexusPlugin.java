package fr.heneria.nexus;

import fr.heneria.nexus.core.config.ConfigurationService;
import fr.heneria.nexus.core.config.MainConfig;
import fr.heneria.nexus.core.config.MessagesConfig;
import fr.heneria.nexus.core.service.*;
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
    private ServiceRegistry serviceRegistry;

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

        try {
            this.serviceRegistry = new ServiceRegistry(this);

            registerServices();

            serviceRegistry.initializeAll();

            serviceRegistry.startAll();

            getLogger().info("========================================");
            getLogger().info("✓ Nexus activé avec succès !");
            getLogger().info("========================================");

        } catch (ServiceException e) {
            getLogger().severe("========================================");
            getLogger().severe("✗ ERREUR FATALE lors du démarrage !");
            getLogger().severe("Service : " + e.getServiceName());
            getLogger().severe("Phase : " + e.getPhase().getDisplayName());
            getLogger().severe("Raison : " + e.getMessage());
            getLogger().severe("========================================");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Arrêt du plugin Nexus...");

        if (serviceRegistry != null) {
            serviceRegistry.shutdownAll();
        }

        getLogger().info("========================================");
        getLogger().info("Nexus désactivé.");
        getLogger().info("========================================");
    }

    /**
     * Enregistre tous les services dans le registre.
     * L'ordre est important : services sans dépendances d'abord.
     */
    private void registerServices() {
        getLogger().info("Enregistrement des services...");

        // Configuration en premier car les autres services en dépendent
        serviceRegistry.register(ConfigurationService.class, new ConfigurationService(this));

        serviceRegistry.register(MapService.class, new MapService(this));
        serviceRegistry.register(ProfileService.class, new ProfileService(this));
        serviceRegistry.register(EconomyService.class, new EconomyService(this));

        serviceRegistry.register(ArenaService.class, new ArenaService(this));
        serviceRegistry.register(QueueService.class, new QueueService(this));

        getLogger().info("Tous les services enregistrés.");
    }

    /**
     * Récupère l'instance du plugin
     *
     * @return Instance singleton de NexusPlugin
     */
    public static NexusPlugin getInstance() {
        return instance;
    }

    /**
     * Récupère le registre de services
     *
     * @return ServiceRegistry du plugin
     */
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    /**
     * Raccourci vers la configuration principale.
     *
     * @return Instance de MainConfig
     */
    public MainConfig getMainConfig() {
        return getServiceRegistry().get(ConfigurationService.class).getMainConfig();
    }

    /**
     * Raccourci vers la configuration des messages.
     *
     * @return Instance de MessagesConfig
     */
    public MessagesConfig getMessagesConfig() {
        return getServiceRegistry().get(ConfigurationService.class).getMessagesConfig();
    }
}
