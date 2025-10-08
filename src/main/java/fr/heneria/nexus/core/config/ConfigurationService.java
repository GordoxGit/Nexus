package fr.heneria.nexus.core.config;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.core.service.AbstractService;

import java.util.ArrayList;
import java.util.List;

/**
 * Service de gestion des configurations.
 * Charge, valide et recharge tous les fichiers de configuration.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ConfigurationService extends AbstractService {

    private MainConfig mainConfig;
    private MessagesConfig messagesConfig;
    // TODO T-021: Ajouter MapConfig
    // TODO T-143: Ajouter EconomyConfig
    // TODO T-088: Ajouter ClassesConfig

    private final List<ConfigurationProvider> allConfigs = new ArrayList<>();

    public ConfigurationService(NexusPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "ConfigurationService";
    }

    @Override
    protected void onInitialize() throws Exception {
        logger.info("Chargement des fichiers de configuration...");

        // Créer les instances
        mainConfig = new MainConfig(plugin);
        messagesConfig = new MessagesConfig(plugin);

        // Ajouter à la liste
        allConfigs.add(mainConfig);
        allConfigs.add(messagesConfig);

        // Charger tous les fichiers
        for (ConfigurationProvider config : allConfigs) {
            try {
                config.load();
            } catch (ConfigException e) {
                logger.severe("✗ Erreur lors du chargement de " + config.getFileName());
                logger.severe("  " + e.getMessage());
                throw e;
            }
        }

        logger.info("✓ Toutes les configurations chargées avec succès");
    }

    @Override
    protected void onStart() throws Exception {
        // Rien à démarrer pour les configs
    }

    @Override
    protected void onShutdown() throws Exception {
        logger.info("Sauvegarde des configurations si nécessaire...");
        // Les configs sont sauvegardées à la volée, rien à faire ici
    }

    /**
     * Recharge toutes les configurations.
     *
     * @throws ConfigException si une erreur survient
     */
    public void reloadAll() throws ConfigException {
        logger.info("========================================");
        logger.info("Rechargement de toutes les configurations...");
        logger.info("========================================");

        long startTime = System.currentTimeMillis();

        for (ConfigurationProvider config : allConfigs) {
            try {
                config.reload();
            } catch (ConfigException e) {
                logger.severe("✗ Erreur lors du rechargement de " + config.getFileName());
                logger.severe("  " + e.getMessage());
                throw e;
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        logger.info("========================================");
        logger.info("✓ Toutes les configurations rechargées en " + duration + "ms");
        logger.info("========================================");
    }

    /**
     * Recharge une configuration spécifique.
     *
     * @param fileName Nom du fichier (ex: "config.yml")
     * @throws ConfigException si erreur ou fichier introuvable
     */
    public void reload(String fileName) throws ConfigException {
        for (ConfigurationProvider config : allConfigs) {
            if (config.getFileName().equals(fileName)) {
                config.reload();
                return;
            }
        }
        throw new ConfigException(fileName, "Fichier de configuration introuvable");
    }

    // === GETTERS ===

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }
}
