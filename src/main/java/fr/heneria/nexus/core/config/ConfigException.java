package fr.heneria.nexus.core.config;

/**
 * Exception levée lors d'erreurs de configuration.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ConfigException extends Exception {

    private final String fileName;
    private final String key;

    /**
     * Constructeur avec fichier et message
     *
     * @param fileName Nom du fichier en erreur
     * @param message Message d'erreur
     */
    public ConfigException(String fileName, String message) {
        super(String.format("[%s] %s", fileName, message));
        this.fileName = fileName;
        this.key = null;
    }

    /**
     * Constructeur avec fichier, clé et message
     *
     * @param fileName Nom du fichier
     * @param key Clé de configuration problématique
     * @param message Message d'erreur
     */
    public ConfigException(String fileName, String key, String message) {
        super(String.format("[%s] Clé '%s' - %s", fileName, key, message));
        this.fileName = fileName;
        this.key = key;
    }

    /**
     * Constructeur avec cause
     *
     * @param fileName Nom du fichier
     * @param message Message d'erreur
     * @param cause Exception originale
     */
    public ConfigException(String fileName, String message, Throwable cause) {
        super(String.format("[%s] %s", fileName, message), cause);
        this.fileName = fileName;
        this.key = null;
    }

    public String getFileName() {
        return fileName;
    }

    public String getKey() {
        return key;
    }
}
