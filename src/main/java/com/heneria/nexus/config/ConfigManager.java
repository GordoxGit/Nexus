package com.heneria.nexus.config;

import com.heneria.nexus.util.NexusLogger;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles configuration files and provides atomic reloads.
 */
public final class ConfigManager {

    private static final String DEFAULT_LANGUAGE = "fr";
    private static final String DEFAULT_TIMEZONE = "Europe/Paris";
    private static final String DEFAULT_JDBC = "jdbc:mariadb://127.0.0.1:3306/nexus";
    private static final String DEFAULT_DB_USER = "nexus";
    private static final String DEFAULT_DB_PASSWORD = "change_me";

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final Path dataDirectory;
    private final AtomicReference<ConfigBundle> bundleRef = new AtomicReference<>();

    public ConfigManager(JavaPlugin plugin, NexusLogger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = plugin.getDataFolder().toPath();
    }

    public LoadResult initialLoad() {
        ensureDefaults();
        try {
            ConfigBundle bundle = loadBundle();
            bundleRef.set(bundle);
            return LoadResult.success(bundle);
        } catch (ConfigLoadException exception) {
            logger.error("Configuration invalide lors du démarrage", exception);
            return LoadResult.failure(exception.errors());
        }
    }

    public LoadResult reloadFromDisk() {
        try {
            ConfigBundle bundle = loadBundle();
            return LoadResult.success(bundle);
        } catch (ConfigLoadException exception) {
            logger.warn("Rechargement de configuration impossible", exception);
            return LoadResult.failure(exception.errors());
        }
    }

    public ConfigBundle currentBundle() {
        return Optional.ofNullable(bundleRef.get())
                .orElseThrow(() -> new IllegalStateException("Configuration not loaded yet"));
    }

    public void applyBundle(ConfigBundle bundle) {
        bundleRef.set(bundle);
    }

    private void ensureDefaults() {
        try {
            if (Files.notExists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create plugin data directory", exception);
        }

        copyResourceIfMissing("config.yml");
        copyResourceIfMissing("messages.yml");
        copyResourceIfMissing("economy.yml");
        copyResourceIfMissing("maps.yml");
    }

    private void copyResourceIfMissing(String resource) {
        File target = dataDirectory.resolve(resource).toFile();
        if (!target.exists()) {
            plugin.saveResource(resource, false);
        }
    }

    private ConfigBundle loadBundle() throws ConfigLoadException {
        File configFile = dataDirectory.resolve("config.yml").toFile();
        File messagesFile = dataDirectory.resolve("messages.yml").toFile();

        YamlConfiguration configYaml = YamlConfiguration.loadConfiguration(configFile);
        applyDefaults(configYaml, "config.yml");
        YamlConfiguration messagesYaml = YamlConfiguration.loadConfiguration(messagesFile);
        applyDefaults(messagesYaml, "messages.yml");

        List<String> errors = new ArrayList<>();
        NexusConfig config = parseConfig(configYaml, errors);
        MessageBundle messages = parseMessages(messagesYaml, config.language(), errors);

        if (!errors.isEmpty()) {
            throw new ConfigLoadException(errors);
        }

        return new ConfigBundle(config, messages, Instant.now());
    }

    private void applyDefaults(YamlConfiguration configuration, String resourceName) {
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                return;
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            configuration.setDefaults(defaults);
            configuration.options().copyDefaults(true);
        } catch (IOException | InvalidConfigurationException exception) {
            logger.warn("Impossible de charger les valeurs par défaut pour " + resourceName, exception);
        }
    }

    private NexusConfig parseConfig(FileConfiguration yaml, List<String> errors) {
        String serverMode = yaml.getString("server.mode", "nexus");
        String languageTag = yaml.getString("server.language", DEFAULT_LANGUAGE);
        Locale language = Locale.forLanguageTag(languageTag);
        String timezoneId = yaml.getString("server.timezone", DEFAULT_TIMEZONE);
        ZoneId timezone;
        try {
            timezone = ZoneId.of(timezoneId);
        } catch (Exception exception) {
            errors.add("server.timezone: " + exception.getMessage());
            timezone = ZoneId.of(DEFAULT_TIMEZONE);
        }

        int hudHz = yaml.getInt("arena.hud_hz", yaml.getInt("perf.hud_hz", 5));
        if (hudHz <= 0) {
            errors.add("arena.hud_hz doit être > 0");
            hudHz = 5;
        }
        int scoreboardHz = yaml.getInt("arena.scoreboard_hz", yaml.getInt("perf.scoreboard_hz", 3));
        if (scoreboardHz <= 0) {
            errors.add("arena.scoreboard_hz doit être > 0");
            scoreboardHz = 3;
        }
        int particlesSoft = yaml.getInt("arena.particles.soft_cap", yaml.getInt("perf.particles_soft_cap", 1200));
        int particlesHard = yaml.getInt("arena.particles.hard_cap", yaml.getInt("perf.particles_hard_cap", 2000));
        if (particlesHard < particlesSoft) {
            errors.add("arena.particles.hard_cap doit être >= arena.particles.soft_cap");
            particlesHard = particlesSoft;
        }
        int maxEntities = yaml.getInt("arena.budget.max_entities", 200);
        int maxItems = yaml.getInt("arena.budget.max_items", 128);
        int maxProjectiles = yaml.getInt("arena.budget.max_projectiles", 64);
        if (maxEntities < 0 || maxItems < 0 || maxProjectiles < 0) {
            errors.add("arena.budget doit contenir des valeurs >= 0");
            maxEntities = Math.max(0, maxEntities);
            maxItems = Math.max(0, maxItems);
            maxProjectiles = Math.max(0, maxProjectiles);
        }

        int ioPool = yaml.getInt("threads.io_pool", 3);
        if (ioPool <= 0) {
            errors.add("threads.io_pool doit être > 0");
            ioPool = 1;
        }
        int computePool = yaml.getInt("threads.compute_pool", 1);
        if (computePool <= 0) {
            errors.add("threads.compute_pool doit être > 0");
            computePool = 1;
        }

        boolean dbEnabled = yaml.getBoolean("database.enabled", true);
        String jdbcUrl = yaml.getString("database.jdbc", DEFAULT_JDBC);
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            errors.add("database.jdbc manquant");
            jdbcUrl = DEFAULT_JDBC;
        }
        String username = yaml.getString("database.user", DEFAULT_DB_USER);
        if (username == null || username.isBlank()) {
            errors.add("database.user manquant");
            username = DEFAULT_DB_USER;
        }
        String password = yaml.getString("database.password", DEFAULT_DB_PASSWORD);
        if (password == null) {
            password = DEFAULT_DB_PASSWORD;
        }
        int maxSize = yaml.getInt("database.pool.maxSize", 10);
        if (maxSize <= 0) {
            errors.add("database.pool.maxSize doit être > 0");
            maxSize = 10;
        }
        int minIdle = yaml.getInt("database.pool.minIdle", 2);
        if (minIdle < 0) {
            errors.add("database.pool.minIdle doit être >= 0");
            minIdle = 0;
        }
        long timeoutMs = yaml.getLong("database.pool.connTimeoutMs", 3000L);
        if (timeoutMs <= 0L) {
            errors.add("database.pool.connTimeoutMs doit être > 0");
            timeoutMs = 3000L;
        }

        boolean exposeServices = yaml.getBoolean("services.expose-bukkit-services", false);
        long startTimeoutMs = yaml.getLong("timeouts.startMs", 5000L);
        if (startTimeoutMs <= 0L) {
            errors.add("timeouts.startMs doit être > 0");
            startTimeoutMs = 5000L;
        }
        long stopTimeoutMs = yaml.getLong("timeouts.stopMs", 3000L);
        if (stopTimeoutMs <= 0L) {
            errors.add("timeouts.stopMs doit être > 0");
            stopTimeoutMs = 3000L;
        }
        boolean degradedEnabled = yaml.getBoolean("degraded-mode.enabled", true);
        boolean degradedBanner = yaml.getBoolean("degraded-mode.banner", true);
        int queueTick = yaml.getInt("queue.tick_hz", 5);
        if (queueTick <= 0) {
            errors.add("queue.tick_hz doit être > 0");
            queueTick = 5;
        }
        int vipWeight = yaml.getInt("queue.vip_weight", 0);
        if (vipWeight < 0) {
            errors.add("queue.vip_weight doit être >= 0");
            vipWeight = 0;
        }

        NexusConfig.ArenaSettings arenaSettings;
        NexusConfig.ThreadSettings threadSettings;
        NexusConfig.PoolSettings poolSettings;
        try {
            arenaSettings = new NexusConfig.ArenaSettings(hudHz, scoreboardHz, particlesSoft, particlesHard,
                    maxEntities, maxItems, maxProjectiles);
        } catch (IllegalArgumentException exception) {
            errors.add("arena: " + exception.getMessage());
            arenaSettings = new NexusConfig.ArenaSettings(5, 3, particlesSoft, particlesHard, maxEntities, maxItems, maxProjectiles);
        }
        try {
            threadSettings = new NexusConfig.ThreadSettings(ioPool, computePool);
        } catch (IllegalArgumentException exception) {
            errors.add("threads: " + exception.getMessage());
            threadSettings = new NexusConfig.ThreadSettings(Math.max(1, ioPool), Math.max(1, computePool));
        }
        try {
            poolSettings = new NexusConfig.PoolSettings(maxSize, minIdle, timeoutMs);
        } catch (IllegalArgumentException exception) {
            errors.add("database.pool: " + exception.getMessage());
            poolSettings = new NexusConfig.PoolSettings(10, 2, 3000L);
        }
        NexusConfig.DatabaseSettings databaseSettings =
                new NexusConfig.DatabaseSettings(dbEnabled, jdbcUrl, username, password, poolSettings);
        NexusConfig.ServiceSettings serviceSettings = new NexusConfig.ServiceSettings(exposeServices);
        NexusConfig.TimeoutSettings timeoutSettings = new NexusConfig.TimeoutSettings(startTimeoutMs, stopTimeoutMs);
        NexusConfig.DegradedModeSettings degradedModeSettings = new NexusConfig.DegradedModeSettings(degradedEnabled, degradedBanner);
        NexusConfig.QueueSettings queueSettings = new NexusConfig.QueueSettings(queueTick, vipWeight);

        return new NexusConfig(serverMode, language, timezone, arenaSettings, threadSettings, databaseSettings,
                serviceSettings, timeoutSettings, degradedModeSettings, queueSettings);
    }

    private MessageBundle parseMessages(FileConfiguration yaml, Locale locale, List<String> errors) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : yaml.getKeys(true)) {
            Object value = yaml.get(key);
            if (value instanceof String stringValue) {
                map.put(key, stringValue);
            }
        }
        if (map.isEmpty()) {
            errors.add("messages: aucune entrée chargée");
        }
        return new MessageBundle(map, locale, Instant.now());
    }

    public record LoadResult(boolean success, ConfigBundle bundle, List<String> errors) {

        public static LoadResult success(ConfigBundle bundle) {
            return new LoadResult(true, bundle, List.of());
        }

        public static LoadResult failure(List<String> errors) {
            return new LoadResult(false, null, List.copyOf(errors));
        }
    }
}
