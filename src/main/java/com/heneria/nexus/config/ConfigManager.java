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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;
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
    private static final String MAPS_FILE = "maps.yml";
    private static final String ECONOMY_FILE = "economy.yml";
    private static final String HOLOGRAMS_FILE = "holograms.yml";
    private static final String LANG_DIRECTORY = "lang";
    private static final String MAPS_DIRECTORY = "maps";
    private static final String WORLD_TEMPLATES_DIRECTORY = "world_templates";
    private static final String EXAMPLE_MAP_DIRECTORY = "example_map";
    private static final String EXAMPLE_MAP_FILE = "map.yml";
    private static final String DEFAULT_LANGUAGE_FILENAME = "messages_fr.yml";
    private static final String DEFAULT_LANGUAGE_FILE = "lang/" + DEFAULT_LANGUAGE_FILENAME;
    private static final String MESSAGE_FILE_PREFIX = "messages_";
    private static final String MESSAGE_FILE_SUFFIX = ".yml";

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final Path dataDirectory;
    private final ExecutorService ioExecutor;
    private final ConfigValidator validator = new ConfigValidator();
    private final ConfigHotSwap hotSwap = new ConfigHotSwap();
    private final ConfigMigrator migrator;

    public ConfigManager(JavaPlugin plugin, NexusLogger logger, BackupService backupService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = plugin.getDataFolder().toPath();
        this.ioExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                new NamedThreadFactory("Nexus-Config", true, logger));
        this.migrator = new ConfigMigrator(logger, Objects.requireNonNull(backupService, "backupService"));
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

    public MessageCatalog getMessages() {
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

        MessageCatalog messages = loadMessageBundles(core.language(), builder);

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
        boolean loaded = false;
        try {
            configuration.load(file);
            loaded = true;
        } catch (IOException | InvalidConfigurationException exception) {
            issues.error("<root>", "Lecture impossible: " + exception.getMessage());
        }
        YamlConfiguration defaults = loadDefaults(resourceName, issues);
        if (defaults != null) {
            if (loaded) {
                try {
                    migrator.migrate(dataDirectory, resourceName, configuration, defaults);
                } catch (IOException exception) {
                    issues.error("<root>", "Migration impossible: " + exception.getMessage());
                    logger.error("Impossible de migrer le fichier '" + resourceName + "'", exception);
                }
            }
            configuration.setDefaults(defaults);
            configuration.options().copyDefaults(true);
        }
        return configuration;
    }

    private YamlConfiguration loadDefaults(String resourceName, ConfigValidator.IssueCollector issues) {
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                issues.warn("<root>", "Ressource embarquée manquante: " + resourceName);
                return null;
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            return defaults;
        } catch (IOException | InvalidConfigurationException exception) {
            issues.warn("<root>", "Impossible de charger les valeurs par défaut pour "
                    + resourceName + ": " + exception.getMessage());
            return null;
        }
    }

    private void ensureDefaults() {
        try {
            if (Files.notExists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path langPath = dataDirectory.resolve(LANG_DIRECTORY);
            if (Files.notExists(langPath)) {
                Files.createDirectories(langPath);
            }
            Path mapsPath = dataDirectory.resolve(MAPS_DIRECTORY);
            if (Files.notExists(mapsPath)) {
                Files.createDirectories(mapsPath);
            }
            Path worldTemplatesPath = dataDirectory.resolve(WORLD_TEMPLATES_DIRECTORY);
            if (Files.notExists(worldTemplatesPath)) {
                Files.createDirectories(worldTemplatesPath);
            }
            createExampleMapIfNeeded(mapsPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de créer le dossier de données du plugin", exception);
        }
        copyResourceIfMissing(CONFIG_FILE);
        copyResourceIfMissing(DEFAULT_LANGUAGE_FILE);
        copyResourceIfMissing(MAPS_FILE);
        copyResourceIfMissing(ECONOMY_FILE);
        copyResourceIfMissing(HOLOGRAMS_FILE);
    }

    private void copyResourceIfMissing(String resource) {
        Path targetPath = dataDirectory.resolve(resource);
        File target = targetPath.toFile();
        if (!target.exists()) {
            try {
                Path parent = targetPath.getParent();
                if (parent != null && Files.notExists(parent)) {
                    Files.createDirectories(parent);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Impossible de préparer le dossier pour " + resource, exception);
            }
            plugin.saveResource(resource, false);
        }
    }

    private void createExampleMapIfNeeded(Path mapsDirectory) throws IOException {
        Path exampleDirectory = mapsDirectory.resolve(EXAMPLE_MAP_DIRECTORY);
        if (Files.exists(exampleDirectory)) {
            return;
        }
        Files.createDirectories(exampleDirectory);
        Path mapFile = exampleDirectory.resolve(EXAMPLE_MAP_FILE);
        if (Files.exists(mapFile)) {
            return;
        }
        String exampleContent = """
# Exemple de configuration pour une carte Nexus
asset:
  type: SCHEMATIC
  file: example_map.schem

rules:
  min_players: 4
  max_players: 12

teams:
  red:
    name: "Rouge"
    spawn:
      x: 0
      y: 64
      z: 0
    nexus:
      hp: 100
      position:
        x: 5
        y: 65
        z: 5
  blue:
    name: "Bleu"
    spawn:
      x: 10
      y: 64
      z: 10
    nexus:
      hp: 100
      position:
        x: 15
        y: 65
        z: 15
""";
        Files.writeString(mapFile, exampleContent, StandardCharsets.UTF_8);
    }

    private MessageCatalog loadMessageBundles(Locale configuredFallback, ReloadReport.Builder builder) {
        List<MessageBundle> loaded = new ArrayList<>();
        MessageBundle frenchBundle = null;

        ConfigValidator.IssueCollector defaultIssues = validator.collector(DEFAULT_LANGUAGE_FILE, builder);
        YamlConfiguration defaultYaml = loadYaml(DEFAULT_LANGUAGE_FILE, defaultIssues);
        Locale expectedDefault = localeFromFilename(DEFAULT_LANGUAGE_FILENAME);
        MessageBundle defaultBundle = validator.validateMessages(defaultYaml, expectedDefault, defaultIssues);
        if (!defaultIssues.hasErrors()) {
            frenchBundle = defaultBundle;
            loaded.add(defaultBundle);
        }

        Path langDirectory = dataDirectory.resolve(LANG_DIRECTORY);
        if (Files.exists(langDirectory) && Files.isDirectory(langDirectory)) {
            try (Stream<Path> stream = Files.list(langDirectory)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith(MESSAGE_FILE_PREFIX)
                                    && name.endsWith(MESSAGE_FILE_SUFFIX)
                                    && !name.equalsIgnoreCase(DEFAULT_LANGUAGE_FILENAME);
                        })
                        .sorted((left, right) -> left.getFileName().toString()
                                .compareToIgnoreCase(right.getFileName().toString()))
                        .forEach(path -> {
                            String resourceName = dataDirectory.relativize(path)
                                    .toString()
                                    .replace(File.separatorChar, '/');
                            ConfigValidator.IssueCollector issues = validator.collector(resourceName, builder);
                            Locale expectedLocale = localeFromFilename(path.getFileName().toString());
                            YamlConfiguration yaml = loadYaml(resourceName, issues);
                            MessageBundle bundle = validator.validateMessages(yaml, expectedLocale, issues);
                            if (!issues.hasErrors()) {
                                loaded.add(bundle);
                            }
                        });
            } catch (IOException exception) {
                builder.error(LANG_DIRECTORY, "<root>",
                        "Impossible de lister les fichiers de langue: " + exception.getMessage());
            }
        } else {
            builder.warn(LANG_DIRECTORY, "<root>",
                    "Dossier de langues introuvable, utilisation des ressources par défaut.");
        }

        if (loaded.isEmpty()) {
            throw new IllegalStateException("Aucun fichier de messages valide chargé");
        }

        MessageBundle fallback = selectFallbackBundle(loaded, configuredFallback, frenchBundle, builder);
        return new MessageCatalog(loaded, fallback);
    }

    private MessageBundle selectFallbackBundle(List<MessageBundle> bundles,
                                               Locale configuredFallback,
                                               MessageBundle frenchBundle,
                                               ReloadReport.Builder builder) {
        MessageBundle match = findBundleForLocale(bundles, configuredFallback);
        if (match != null) {
            return match;
        }
        if (frenchBundle != null) {
            if (configuredFallback != null
                    && !"fr".equalsIgnoreCase(configuredFallback.getLanguage())) {
                builder.warn(CONFIG_FILE, "server.language",
                        "Locale " + configuredFallback.toLanguageTag()
                                + " introuvable, utilisation du français.");
            }
            return frenchBundle;
        }
        MessageBundle fallback = bundles.get(0);
        if (configuredFallback != null) {
            builder.warn(CONFIG_FILE, "server.language",
                    "Locale " + configuredFallback.toLanguageTag()
                            + " introuvable, utilisation de " + fallback.locale().toLanguageTag());
        }
        return fallback;
    }

    private MessageBundle findBundleForLocale(List<MessageBundle> bundles, Locale locale) {
        if (locale == null) {
            return null;
        }
        String tag = locale.toLanguageTag();
        for (MessageBundle bundle : bundles) {
            if (bundle.locale().toLanguageTag().equalsIgnoreCase(tag)) {
                return bundle;
            }
        }
        String language = locale.getLanguage();
        if (!language.isEmpty()) {
            for (MessageBundle bundle : bundles) {
                if (bundle.locale().getLanguage().equalsIgnoreCase(language)) {
                    return bundle;
                }
            }
        }
        return null;
    }

    private Locale localeFromFilename(String fileName) {
        if (!fileName.startsWith(MESSAGE_FILE_PREFIX) || !fileName.endsWith(MESSAGE_FILE_SUFFIX)) {
            return null;
        }
        String tag = fileName.substring(MESSAGE_FILE_PREFIX.length(),
                fileName.length() - MESSAGE_FILE_SUFFIX.length());
        tag = tag.replace('_', '-');
        Locale locale = Locale.forLanguageTag(tag);
        if (locale.toLanguageTag().isEmpty()) {
            return null;
        }
        return locale;
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
                              MessageCatalog messages,
                              MapsCatalogConfig maps,
                              EconomyConfig economy,
                              ReloadReport.Builder report) {
    }
}
