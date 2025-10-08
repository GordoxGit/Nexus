package fr.heneria.nexus.core.config;

import fr.heneria.nexus.NexusPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Classe de base pour tous les fichiers de configuration.
 * Fournit les mécanismes de chargement, sauvegarde et validation.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public abstract class BaseConfig implements ConfigurationProvider {

    protected final NexusPlugin plugin;
    protected final Logger logger;
    protected final String fileName;
    protected final File file;
    protected YamlConfiguration config;

    /**
     * Constructeur de base
     *
     * @param plugin Instance du plugin
     * @param fileName Nom du fichier (ex: "config.yml")
     */
    protected BaseConfig(NexusPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);
    }

    @Override
    public void load() throws ConfigException {
        try {
            // Créer le dossier si nécessaire
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Si le fichier n'existe pas, copier depuis les ressources
            if (!file.exists()) {
                logger.info("Création de " + fileName + "...");
                saveDefaultConfig();
            }

            // Charger le fichier
            config = YamlConfiguration.loadConfiguration(file);

            // Valider
            validate();

            logger.info("✓ " + fileName + " chargé (" + countKeys() + " clés)");

        } catch (Exception e) {
            throw new ConfigException(fileName, "Erreur lors du chargement", e);
        }
    }

    @Override
    public void save() throws ConfigException {
        try {
            config.save(file);
            logger.info("✓ " + fileName + " sauvegardé");
        } catch (IOException e) {
            throw new ConfigException(fileName, "Erreur lors de la sauvegarde", e);
        }
    }

    @Override
    public void reload() throws ConfigException {
        logger.info("Rechargement de " + fileName + "...");

        // Sauvegarde de l'ancienne config au cas où
        YamlConfiguration oldConfig = config;

        try {
            // Recharger depuis le fichier
            config = YamlConfiguration.loadConfiguration(file);

            // Valider
            validate();

            logger.info("✓ " + fileName + " rechargé avec succès");

        } catch (Exception e) {
            // Restaurer l'ancienne config en cas d'erreur
            config = oldConfig;
            throw new ConfigException(fileName, "Erreur lors du rechargement, ancienne config restaurée", e);
        }
    }

    /**
     * Sauvegarde le fichier par défaut depuis les ressources.
     *
     * @throws IOException si l'écriture échoue
     */
    private void saveDefaultConfig() throws IOException {
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                // Créer un fichier vide si pas de ressource
                file.createNewFile();
                config = new YamlConfiguration();
                setDefaults();
                config.save(file);
            } else {
                // Copier depuis les ressources
                Files.copy(in, file.toPath());
            }
        }
    }

    /**
     * Compte le nombre de clés dans la configuration.
     *
     * @return Nombre de clés
     */
    private int countKeys() {
        return config.getKeys(true).size();
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }

    /**
     * Définit les valeurs par défaut si le fichier n'existe pas dans les ressources.
     * À implémenter par les classes filles.
     */
    protected abstract void setDefaults();

    /**
     * Méthode utilitaire pour vérifier qu'une clé existe.
     *
     * @param key Clé à vérifier
     * @throws ConfigException si la clé est absente
     */
    protected void requireKey(String key) throws ConfigException {
        if (!config.contains(key)) {
            throw new ConfigException(fileName, key, "Clé requise manquante");
        }
    }

    /**
     * Méthode utilitaire pour vérifier qu'un nombre est dans un range.
     *
     * @param key Clé de la valeur
     * @param value Valeur à vérifier
     * @param min Minimum (inclus)
     * @param max Maximum (inclus)
     * @throws ConfigException si hors limites
     */
    protected void requireRange(String key, int value, int min, int max) throws ConfigException {
        if (value < min || value > max) {
            throw new ConfigException(fileName, key,
                String.format("Valeur %d hors limites [%d, %d]", value, min, max));
        }
    }

    /**
     * Méthode utilitaire pour vérifier qu'un nombre est positif.
     *
     * @param key Clé de la valeur
     * @param value Valeur à vérifier
     * @throws ConfigException si négatif
     */
    protected void requirePositive(String key, int value) throws ConfigException {
        if (value < 0) {
            throw new ConfigException(fileName, key, "La valeur doit être positive");
        }
    }
}
