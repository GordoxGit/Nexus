package com.heneria.nexus.config;

import com.heneria.nexus.util.NamedThreadFactory;
import com.heneria.nexus.util.NexusLogger;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles configuration files and provides atomic reloads with validation.
 */
public final class ConfigManager implements AutoCloseable {

    private static final String CONFIG_FILE = "config.yml";
    private static final String MESSAGES_FILE = "messages.yml";
    private static final String MAPS_FILE = "maps.yml";
    private static final String ECONOMY_FILE = "economy.yml";

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final Path dataDirectory;
    private final ExecutorService ioExecutor;
    private final ConfigValidator validator = new ConfigValidator();
    private final ConfigHotSwap hotSwap = new ConfigHotSwap();

    public ConfigManager(JavaPlugin plugin, NexusLogger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = plugin.getDataFolder().toPath();
        this.ioExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                new NamedThreadFactory("Nexus-Config", true, logger));
    }

    public ReloadReport initialLoad() {
        ensureDefaults();
        try {
            LoadResult result = CompletableFuture.supplyAsync(this::loadAll, ioExecutor).join();
            ReloadReport.Builder builder = result.report();
            if (builder.hasErrors()) {
                builder.version(0L);
                return builder.buildFailure();
            }
            ConfigBundle bundle = new ConfigBundle(1L, result.core(), result.messages(), result.maps(), result.economy(), Instant.now());
            hotSwap.initialize(bundle);
            builder.version(bundle.version());
            logger.info("Configuration initialisée (version " + bundle.version() + ")");
            return builder.buildSuccess();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            logger.error("Impossible de charger la configuration", cause);
            ReloadReport.Builder builder = ReloadReport.builder(Instant.now());
            builder.error(CONFIG_FILE, "<root>", cause.getMessage() == null ? cause.toString() : cause.getMessage());
            builder.version(0L);
            return builder.buildFailure();
        }
    }

    public CompletionStage<ReloadReport> reloadAllAsync(CommandSender initiator) {
        Objects.requireNonNull(initiator, "initiator");
        ensureDefaults();
        logger.info("Rechargement demandé par " + initiator.getName());
        return CompletableFuture.supplyAsync(this::loadAll, ioExecutor)
                .thenCompose(result -> {
                    ReloadReport.Builder builder = result.report();
                    if (builder.hasErrors()) {
                        builder.version(hotSwap.currentVersion());
                        return CompletableFuture.completedFuture(builder.buildFailure());
                    }
                    long version = hotSwap.currentVersion() + 1;
                    ConfigBundle bundle = new ConfigBundle(version, result.core(), result.messages(), result.maps(), result.economy(), Instant.now());
                    builder.version(bundle.version());
                    return callSync(() -> {
                        hotSwap.commit(bundle);
                        logger.info("Configuration rechargée (version " + bundle.version() + ")");
                        return builder.buildSuccess();
                    });
                })
                .exceptionally(throwable -> {
                    Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                            ? throwable.getCause()
                            : throwable;
                    logger.error("Erreur lors du rechargement de la configuration", cause);
                    ReloadReport.Builder builder = ReloadReport.builder(Instant.now());
                    builder.error(CONFIG_FILE, "<root>", cause.getMessage() == null ? cause.toString() : cause.getMessage());
                    builder.version(hotSwap.currentVersion());
                    return builder.buildFailure();
                });
    }

    public CoreConfig getCore() {
        return currentBundle().core();
    }

    public MessageBundle getMessages() {
        return currentBundle().messages();
    }

    public MapsCatalogConfig getMaps() {
        return currentBundle().maps();
    }

    public EconomyConfig getEconomy() {
        return currentBundle().economy();
    }

    public long version() {
        return hotSwap.currentVersion();
    }

    public ConfigBundle currentBundle() {
        return hotSwap.current();
    }

    private LoadResult loadAll() {
        ReloadReport.Builder builder = ReloadReport.builder(Instant.now());

        ConfigValidator.IssueCollector coreIssues = validator.collector(CONFIG_FILE, builder);
        YamlConfiguration coreYaml = loadYaml(CONFIG_FILE, coreIssues);
        CoreConfig core = validator.validateCore(coreYaml, coreIssues);

        ConfigValidator.IssueCollector messageIssues = validator.collector(MESSAGES_FILE, builder);
        YamlConfiguration messageYaml = loadYaml(MESSAGES_FILE, messageIssues);
        MessageBundle messages = validator.validateMessages(messageYaml, messageIssues);

        ConfigValidator.IssueCollector mapsIssues = validator.collector(MAPS_FILE, builder);
        YamlConfiguration mapsYaml = loadYaml(MAPS_FILE, mapsIssues);
        MapsCatalogConfig maps = validator.validateMaps(mapsYaml, mapsIssues);

        ConfigValidator.IssueCollector economyIssues = validator.collector(ECONOMY_FILE, builder);
        YamlConfiguration economyYaml = loadYaml(ECONOMY_FILE, economyIssues);
        EconomyConfig economy = validator.validateEconomy(economyYaml, economyIssues);

        return new LoadResult(core, messages, maps, economy, builder);
    }

    private YamlConfiguration loadYaml(String resourceName, ConfigValidator.IssueCollector issues) {
        File file = dataDirectory.resolve(resourceName).toFile();
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            issues.error("<root>", "Lecture impossible: " + exception.getMessage());
        }
        applyDefaults(configuration, resourceName, issues);
        return configuration;
    }

    private void applyDefaults(YamlConfiguration configuration, String resourceName, ConfigValidator.IssueCollector issues) {
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                issues.warn("<root>", "Ressource embarquée manquante: " + resourceName);
                return;
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            configuration.setDefaults(defaults);
            configuration.options().copyDefaults(true);
        } catch (IOException | InvalidConfigurationException exception) {
            issues.warn("<root>", "Impossible de charger les valeurs par défaut pour " + resourceName + ": " + exception.getMessage());
        }
    }

    private void ensureDefaults() {
        try {
            if (Files.notExists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de créer le dossier de données du plugin", exception);
        }
        copyResourceIfMissing(CONFIG_FILE);
        copyResourceIfMissing(MESSAGES_FILE);
        copyResourceIfMissing(MAPS_FILE);
        copyResourceIfMissing(ECONOMY_FILE);
    }

    private void copyResourceIfMissing(String resource) {
        File target = dataDirectory.resolve(resource).toFile();
        if (!target.exists()) {
            plugin.saveResource(resource, false);
        }
    }

    private <T> CompletionStage<T> callSync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    @Override
    public void close() {
        ioExecutor.shutdownNow();
    }

    private record LoadResult(CoreConfig core,
                              MessageBundle messages,
                              MapsCatalogConfig maps,
                              EconomyConfig economy,
                              ReloadReport.Builder report) {
    }
}
