package fr.heneria.nexus.core.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Interface pour les fournisseurs de configuration.
 * Définit le contrat de chargement, sauvegarde et rechargement des fichiers de config.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public interface ConfigurationProvider {

    /**
     * Charge la configuration depuis le fichier.
     * Si le fichier n'existe pas, crée les valeurs par défaut.
     *
     * @throws ConfigException si le chargement échoue
     */
    void load() throws ConfigException;

    /**
     * Sauvegarde la configuration actuelle dans le fichier.
     *
     * @throws ConfigException si la sauvegarde échoue
     */
    void save() throws ConfigException;

    /**
     * Recharge la configuration depuis le fichier.
     * Les anciennes valeurs sont remplacées atomiquement.
     *
     * @throws ConfigException si le rechargement échoue
     */
    void reload() throws ConfigException;

    /**
     * Retourne le nom du fichier de configuration.
     *
     * @return Nom du fichier (ex: "config.yml")
     */
    String getFileName();

    /**
     * Retourne le fichier de configuration.
     *
     * @return Objet File
     */
    File getFile();

    /**
     * Retourne la configuration Bukkit sous-jacente.
     *
     * @return YamlConfiguration
     */
    YamlConfiguration getConfig();

    /**
     * Valide la configuration après chargement.
     * Vérifie les valeurs requises, les types, les ranges.
     *
     * @throws ConfigException si la validation échoue
     */
    void validate() throws ConfigException;
}
