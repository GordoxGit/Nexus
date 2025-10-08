package fr.heneria.nexus.core.config;

import fr.heneria.nexus.NexusPlugin;

/**
 * Configuration principale du plugin (config.yml).
 * Gère les paramètres de base de données, performance et monde.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class MainConfig extends BaseConfig {

    // Cache des valeurs fréquemment accédées
    private boolean databaseEnabled;
    private String databaseHost;
    private int databasePort;
    private String databaseName;
    private String databaseUsername;
    private String databasePassword;

    private int msptWarning;
    private int msptCritical;
    private int maxEntitiesPerArena;
    private int maxParticlesPerPlayer;

    private String resetMethod;
    private int resetThreshold;

    private boolean debugEnabled;

    public MainConfig(NexusPlugin plugin) {
        super(plugin, "config.yml");
    }

    @Override
    public void validate() throws ConfigException {
        // Vérifier les clés essentielles
        requireKey("config-version");
        requireKey("database.enabled");
        requireKey("database.host");
        requireKey("database.port");
        requireKey("performance.mspt-thresholds.warning");
        requireKey("performance.mspt-thresholds.critical");

        // Valider les ranges
        int port = config.getInt("database.port");
        requireRange("database.port", port, 1, 65535);

        int msptWarn = config.getInt("performance.mspt-thresholds.warning");
        int msptCrit = config.getInt("performance.mspt-thresholds.critical");
        requirePositive("performance.mspt-thresholds.warning", msptWarn);
        requirePositive("performance.mspt-thresholds.critical", msptCrit);

        if (msptWarn >= msptCrit) {
            throw new ConfigException(fileName, "performance.mspt-thresholds",
                "Le seuil warning doit être inférieur au seuil critical");
        }

        // Valider reset method
        String method = config.getString("world.reset-method", "DELTA");
        if (!method.equals("DELTA") && !method.equals("REPASTE")) {
            throw new ConfigException(fileName, "world.reset-method",
                "Valeur invalide. Doit être DELTA ou REPASTE");
        }

        // Mettre en cache les valeurs
        cacheValues();
    }

    /**
     * Met en cache les valeurs fréquemment accédées pour éviter les lookups répétés.
     */
    private void cacheValues() {
        databaseEnabled = config.getBoolean("database.enabled");
        databaseHost = config.getString("database.host");
        databasePort = config.getInt("database.port");
        databaseName = config.getString("database.database");
        databaseUsername = config.getString("database.username");
        databasePassword = config.getString("database.password");

        msptWarning = config.getInt("performance.mspt-thresholds.warning");
        msptCritical = config.getInt("performance.mspt-thresholds.critical");
        maxEntitiesPerArena = config.getInt("performance.budgets.max-entities-per-arena");
        maxParticlesPerPlayer = config.getInt("performance.budgets.max-particles-per-player");

        resetMethod = config.getString("world.reset-method");
        resetThreshold = config.getInt("world.reset-threshold-modifications");

        debugEnabled = config.getBoolean("debug.enabled");
    }

    @Override
    protected void setDefaults() {
        // Cette méthode ne sera pas appelée car config.yml existe dans les ressources
        // Mais par sécurité, on peut définir des valeurs
        config.set("config-version", 1);
        config.set("database.enabled", true);
        config.set("database.host", "localhost");
        config.set("database.port", 3306);
    }

    // === GETTERS TYPÉS (API publique) ===

    public boolean isDatabaseEnabled() {
        return databaseEnabled;
    }

    public String getDatabaseHost() {
        return databaseHost;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseUsername() {
        return databaseUsername;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    public int getMsptWarningThreshold() {
        return msptWarning;
    }

    public int getMsptCriticalThreshold() {
        return msptCritical;
    }

    public int getMaxEntitiesPerArena() {
        return maxEntitiesPerArena;
    }

    public int getMaxParticlesPerPlayer() {
        return maxParticlesPerPlayer;
    }

    public String getResetMethod() {
        return resetMethod;
    }

    public int getResetThreshold() {
        return resetThreshold;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Record pour l'URL de connexion JDBC.
     */
    public record DatabaseConnectionInfo(String url, String username, String password) {}

    /**
     * Génère l'URL de connexion JDBC complète.
     *
     * @return Info de connexion
     */
    public DatabaseConnectionInfo getDatabaseConnectionInfo() {
        String url = String.format("jdbc:mariadb://%s:%d/%s", databaseHost, databasePort, databaseName);
        return new DatabaseConnectionInfo(url, databaseUsername, databasePassword);
    }
}
