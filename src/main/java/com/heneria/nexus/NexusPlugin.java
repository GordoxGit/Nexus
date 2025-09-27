package com.heneria.nexus;

import com.heneria.nexus.admin.PlayerDataCodec;
import com.heneria.nexus.admin.PlayerDataExporter;
import com.heneria.nexus.admin.PlayerDataFormat;
import com.heneria.nexus.admin.PlayerDataImporter;
import com.heneria.nexus.admin.PlayerDataImporter.ImportPreparation;
import com.heneria.nexus.admin.PlayerDataValidationException;
import com.heneria.nexus.admin.PlayerEconomySnapshot;
import com.heneria.nexus.admin.PlayerProfileSnapshot;
import com.heneria.nexus.analytics.AnalyticsRepository;
import com.heneria.nexus.analytics.AnalyticsService;
import com.heneria.nexus.audit.AuditActionType;
import com.heneria.nexus.audit.AuditEntry;
import com.heneria.nexus.audit.AuditLogQuery;
import com.heneria.nexus.audit.AuditLogRecord;
import com.heneria.nexus.audit.AuditService;
import com.heneria.nexus.audit.AuditServiceImpl;
import com.heneria.nexus.analytics.daily.DailyStatsAggregatorService;
import com.heneria.nexus.analytics.daily.DailyStatsRepository;
import com.heneria.nexus.budget.BudgetService;
import com.heneria.nexus.budget.BudgetServiceImpl;
import com.heneria.nexus.budget.BudgetSnapshot;
import com.heneria.nexus.command.NexusCommand;
import com.heneria.nexus.config.ConfigBundle;
import com.heneria.nexus.config.ConfigManager;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.config.EconomyConfig;
import com.heneria.nexus.config.ReloadReport;
import com.heneria.nexus.db.DatabaseMigrator;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.db.repository.EconomyRepository;
import com.heneria.nexus.db.repository.EconomyRepositoryImpl;
import com.heneria.nexus.db.repository.MatchRepository;
import com.heneria.nexus.db.repository.MatchRepositoryImpl;
import com.heneria.nexus.db.repository.AuditLogRepository;
import com.heneria.nexus.db.repository.AuditLogRepositoryImpl;
import com.heneria.nexus.db.repository.PlayerClassRepository;
import com.heneria.nexus.db.repository.PlayerClassRepositoryImpl;
import com.heneria.nexus.db.repository.PlayerCosmeticRepository;
import com.heneria.nexus.db.repository.PlayerCosmeticRepositoryImpl;
import com.heneria.nexus.db.repository.ProfileRepository;
import com.heneria.nexus.db.repository.ProfileRepositoryImpl;
import com.heneria.nexus.db.repository.RewardClaimRepository;
import com.heneria.nexus.db.repository.RewardClaimRepositoryImpl;
import com.heneria.nexus.hologram.HoloService;
import com.heneria.nexus.hologram.HoloServiceImpl;
import com.heneria.nexus.hologram.Hologram;
import com.heneria.nexus.hologram.HologramVisibilityListener;
import com.heneria.nexus.scheduler.GamePhase;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.scheduler.RingScheduler.TaskProfile;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.service.ServiceRegistry;
import com.heneria.nexus.api.ArenaService;
import com.heneria.nexus.api.EconomyService;
import com.heneria.nexus.api.MapService;
import com.heneria.nexus.api.ProfileService;
import com.heneria.nexus.api.RewardService;
import com.heneria.nexus.api.QueueService;
import com.heneria.nexus.api.ShopService;
import com.heneria.nexus.api.service.TimerService;
import com.heneria.nexus.service.core.ArenaServiceImpl;
import com.heneria.nexus.service.core.EconomyServiceImpl;
import com.heneria.nexus.service.core.MapServiceImpl;
import com.heneria.nexus.service.core.PersistenceService;
import com.heneria.nexus.service.core.PersistenceServiceImpl;
import com.heneria.nexus.service.core.ProfileServiceImpl;
import com.heneria.nexus.service.core.QueueServiceImpl;
import com.heneria.nexus.service.core.RewardServiceImpl;
import com.heneria.nexus.service.core.ShopServiceImpl;
import com.heneria.nexus.service.core.TimerServiceImpl;
import com.heneria.nexus.service.core.VaultEconomyService;
import com.heneria.nexus.service.maintenance.DataPurgeService;
import com.heneria.nexus.service.permissions.NexusContextManager;
import com.heneria.nexus.util.DumpUtil;
import com.heneria.nexus.util.MessageFacade;
import com.heneria.nexus.util.NexusLogger;
import com.heneria.nexus.watchdog.WatchdogService;
import com.heneria.nexus.watchdog.WatchdogServiceImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

/**
 * Main entry point for the Nexus plugin.
 */
public final class NexusPlugin extends JavaPlugin {

    private static final String LOG_PREFIX = "[NEXUS] ";
    private static final int AUDIT_LOG_PAGE_SIZE = 10;
    private static final int AUDIT_DETAILS_MAX_LENGTH = 160;

    private NexusLogger logger;
    private ConfigManager configManager;
    private ConfigBundle bundle;
    private MessageFacade messageFacade;
    private boolean placeholderApiAvailable;
    private ServiceRegistry serviceRegistry;
    private ExecutorManager executorManager;
    private RingScheduler ringScheduler;
    private DbProvider dbProvider;
    private DatabaseMigrator databaseMigrator;
    private LuckPerms luckPermsApi;
    private NexusContextManager contextManager;
    private boolean bootstrapFailed;
    private boolean servicesExposed;
    private PlayerDataCodec playerDataCodec;
    private PlayerDataExporter playerDataExporter;
    private PlayerDataImporter playerDataImporter;
    private Path exportsDirectory;
    private Path importsDirectory;
    private AuditService auditService;
    private DateTimeFormatter auditTimestampFormatter;

    @Override
    public void onLoad() {
        this.logger = new NexusLogger(getLogger(), LOG_PREFIX);
        this.configManager = new ConfigManager(this, logger);
        ReloadReport report = configManager.initialLoad();
        if (!report.success()) {
            report.errors().forEach(error -> logger.error(formatIssue(error)));
            bootstrapFailed = true;
            return;
        }
        report.warnings().forEach(warning -> logger.warn(formatIssue(warning)));
        this.bundle = configManager.currentBundle();
    }

    @Override
    public void onEnable() {
        if (bootstrapFailed) {
            logger.error("Initialisation de Nexus impossible, désactivation");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!checkDependencies()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialisation des composants critiques qui dépendent du serveur actif
        this.messageFacade = new MessageFacade(bundle.messages(), logger, placeholderApiAvailable);
        this.executorManager = new ExecutorManager(this, logger, bundle.core().executorSettings());
        this.serviceRegistry = new ServiceRegistry(logger);
        this.dbProvider = new DbProvider(logger, this);
        this.databaseMigrator = new DatabaseMigrator(logger, this, dbProvider);

        registerSingletons();
        setupEconomy();
        setupPermissions();
        registerServices();

        try {
            serviceRegistry.wire(Duration.ofMillis(bundle.core().timeoutSettings().startMs()));
            this.ringScheduler = serviceRegistry.get(RingScheduler.class);
            this.dbProvider = serviceRegistry.get(DbProvider.class);
            this.databaseMigrator = serviceRegistry.get(DatabaseMigrator.class);
            ringScheduler.applyPerfSettings(bundle.core().arenaSettings());

            // Démarrage des services
            serviceRegistry.startAll(Duration.ofMillis(bundle.core().timeoutSettings().startMs()));
            initializePlayerDataTools();
            this.auditService = serviceRegistry.get(AuditService.class);
            this.auditTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(bundle.core().timezone());

        } catch (Exception exception) {
            logger.error("Impossible d'initialiser ou de démarrer le registre de services", exception);
            bootstrapFailed = true;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.messageFacade.update(bundle.messages());
        this.messageFacade.updatePlaceholderAvailability(placeholderApiAvailable);
        registerCommands();
        registerListeners();
        logEnvironment();
        configureDatabase(bundle.core().databaseSettings());

        ringScheduler.registerProfile(TaskProfile.HUD,
                EnumSet.of(GamePhase.LOBBY, GamePhase.STARTING, GamePhase.PLAYING),
                () -> {
                });
        ringScheduler.registerProfile(TaskProfile.SCOREBOARD,
                EnumSet.of(GamePhase.STARTING, GamePhase.PLAYING, GamePhase.RESET),
                () -> {
                });
        maybeExposeServices();
    }

    @Override
    public void onDisable() {
        if (contextManager != null) {
            contextManager.close();
            contextManager = null;
        }
        if (servicesExposed) {
            getServer().getServicesManager().unregisterAll(this);
            servicesExposed = false;
        }
        if (serviceRegistry != null) {
            try {
                serviceRegistry.get(PersistenceService.class).flushAllOnShutdown();
            } catch (Exception exception) {
                logger.error("Impossible de flush le cache de persistance avant l'arrêt", exception);
            }
            serviceRegistry.stopAll(Duration.ofMillis(bundle != null ? bundle.core().timeoutSettings().stopMs() : 3000L));
        }
        if (executorManager != null) {
            long await = bundle != null ? bundle.core().executorSettings().shutdown().awaitSeconds() : 5L;
            long force = bundle != null ? bundle.core().executorSettings().shutdown().forceCancelSeconds() : 3L;
            executorManager.shutdownGracefully(Duration.ofSeconds(await + force));
        }
        if (configManager != null) {
            configManager.close();
        }
        logger.info("Nexus désactivé proprement");
    }

    private void configureDatabase(CoreConfig.DatabaseSettings settings) {
        CompletionStage<Boolean> stage = executorManager.withTimeout(
                dbProvider.applyConfiguration(settings, executorManager.io()),
                Duration.ofMillis(bundle.core().timeoutSettings().startMs()));
        CompletableFuture<Boolean> future = stage.toCompletableFuture();
        boolean success;
        try {
            success = future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            logger.warn("Configuration MariaDB impossible", cause);
            return;
        }

        if (!success) {
            if (bundle.core().degradedModeSettings().enabled()) {
                logger.warn("Mode dégradé activé : MariaDB indisponible");
            }
            return;
        }

        if (!settings.enabled()) {
            return;
        }

        try {
            databaseMigrator.migrate();
        } catch (DatabaseMigrator.MigrationException exception) {
            logger.error("Échec de l'application des migrations MariaDB", exception);
            bootstrapFailed = true;
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommands() {
        PluginCommand command = Objects.requireNonNull(getCommand("nexus"), "Command nexus not registered in plugin.yml");
        NexusCommand executor = new NexusCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void initializePlayerDataTools() {
        Path dataPath = getDataFolder().toPath();
        try {
            Files.createDirectories(dataPath);
        } catch (IOException exception) {
            logger.warn("Impossible de préparer le dossier de données Nexus", exception);
        }
        try {
            exportsDirectory = dataPath.resolve("exports");
            Files.createDirectories(exportsDirectory);
        } catch (IOException exception) {
            exportsDirectory = null;
            logger.warn("Impossible de créer le dossier d'export des données joueurs", exception);
        }
        try {
            importsDirectory = dataPath.resolve("imports");
            Files.createDirectories(importsDirectory);
        } catch (IOException exception) {
            importsDirectory = null;
            logger.warn("Impossible de créer le dossier d'import des données joueurs", exception);
        }
        playerDataCodec = new PlayerDataCodec();
        ProfileRepository profileRepository = serviceRegistry.get(ProfileRepository.class);
        EconomyRepository economyRepository = serviceRegistry.get(EconomyRepository.class);
        if (exportsDirectory != null) {
            playerDataExporter = new PlayerDataExporter(profileRepository, economyRepository, executorManager, playerDataCodec,
                    exportsDirectory, logger);
        } else {
            playerDataExporter = null;
        }
        if (importsDirectory != null) {
            playerDataImporter = new PlayerDataImporter(profileRepository, economyRepository, dbProvider, executorManager,
                    playerDataCodec, importsDirectory, logger);
        } else {
            playerDataImporter = null;
        }
    }

    private void registerListeners() {
        PluginManager manager = getServer().getPluginManager();
        manager.registerEvents(new HologramVisibilityListener(serviceRegistry.get(HoloService.class)), this);
    }

    private boolean checkDependencies() {
        PluginManager manager = getServer().getPluginManager();

        this.placeholderApiAvailable = isPluginAvailable(manager, "PlaceholderAPI");
        if (placeholderApiAvailable) {
            logger.info("PlaceholderAPI détecté. Intégration activée.");
        } else {
            logger.warn("PlaceholderAPI non trouvé. Les placeholders PAPI ne seront pas fonctionnels.");
        }
        return true;
    }

    private boolean isPlaceholderApiPresent() {
        return isPluginAvailable(getServer().getPluginManager(), "PlaceholderAPI");
    }

    private boolean isPluginAvailable(PluginManager manager, String name) {
        Plugin plugin = manager.getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    private void logEnvironment() {
        logger.info("Démarrage de Nexus %s".formatted(getDescription().getVersion()));
        logger.info("Java: %s".formatted(System.getProperty("java.version")));
        logger.info("Paper: %s".formatted(getServer().getVersion()));
        logger.info("Fuseau horaire: %s".formatted(bundle.core().timezone()));
        logger.info("HUD: %d Hz, scoreboard: %d Hz".formatted(
                bundle.core().arenaSettings().hudHz(),
                bundle.core().arenaSettings().scoreboardHz()));
        logger.info("Matchmaking: %d Hz".formatted(bundle.core().queueSettings().tickHz()));
        CoreConfig.ExecutorSettings executorSettings = bundle.core().executorSettings();
        String ioMode = executorSettings.io().virtual()
                ? "threads virtuels"
                : "%d threads".formatted(executorSettings.io().maxThreads());
        logger.info("Exécuteurs: IO=%s, compute=%d".formatted(ioMode, executorSettings.compute().size()));
    }

    public void sendHelp(CommandSender sender) {
        messageFacade.send(sender, "help.header");
        messageFacade.messageList(sender, "help.lines").ifPresent(lines -> lines.forEach(sender::sendMessage));
        if (sender.hasPermission("nexus.admin.reload")) {
            messageFacade.send(sender, "help.admin.reload");
        }
        if (sender.hasPermission("nexus.admin.dump")) {
            messageFacade.send(sender, "help.admin.dump");
        }
        if (sender.hasPermission("nexus.admin.budget")) {
            messageFacade.send(sender, "help.admin.budget");
        }
        if (sender.hasPermission("nexus.admin.player.export") || sender.hasPermission("nexus.admin.player.import")) {
            messageFacade.send(sender, "help.admin.player");
        }
        if (sender.hasPermission("nexus.holo.manage")) {
            messageFacade.send(sender, "help.admin.holo");
        }
    }

    public void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nexus.admin.reload")) {
            messageFacade.send(sender, "errors.no_permission");
            return;
        }
        messageFacade.send(sender, "admin.reload.start");
        configManager.reloadAllAsync(sender).whenComplete((report, throwable) ->
                getServer().getScheduler().runTask(this, () -> {
                    if (throwable != null) {
                        messageFacade.send(sender, "admin.reload.fail");
                        logger.error("Erreur lors du rechargement", throwable);
                        return;
                    }
                    if (!report.success()) {
                        messageFacade.send(sender, "admin.reload.fail");
                        report.errors().forEach(error -> {
                            logger.error(formatIssue(error));
                            sender.sendMessage(formatIssue(error, NamedTextColor.RED));
                        });
                        return;
                    }
                    ConfigBundle previous = this.bundle;
                    ConfigBundle refreshed = configManager.currentBundle();
                    try {
                        applyBundle(refreshed);
                        messageFacade.send(sender, "admin.reload.ok");
                        report.warnings().forEach(warning -> {
                            logger.warn(formatIssue(warning));
                            sender.sendMessage(formatIssue(warning, NamedTextColor.YELLOW));
                        });
                    } catch (Exception exception) {
                        this.bundle = previous;
                        messageFacade.send(sender, "admin.reload.fail");
                        logger.error("Erreur lors de l'application de la configuration", exception);
                    }
                }));
    }

    private synchronized void applyBundle(ConfigBundle newBundle) {
        this.placeholderApiAvailable = isPlaceholderApiPresent();
        messageFacade.updatePlaceholderAvailability(placeholderApiAvailable);
        serviceRegistry.updateSingleton(Boolean.class, placeholderApiAvailable);
        executorManager.reconfigure(newBundle.core().executorSettings());
        ringScheduler.applyPerfSettings(newBundle.core().arenaSettings());
        messageFacade.update(newBundle.messages());
        this.bundle = newBundle;
        serviceRegistry.updateSingleton(ConfigBundle.class, newBundle);
        serviceRegistry.updateSingleton(CoreConfig.class, newBundle.core());
        serviceRegistry.updateSingleton(EconomyConfig.class, newBundle.economy());
        configureDatabase(newBundle.core().databaseSettings());
        serviceRegistry.get(DataPurgeService.class).applyConfiguration(newBundle.core());
        serviceRegistry.get(QueueService.class).applySettings(newBundle.core().queueSettings());
        serviceRegistry.get(ArenaService.class).applyArenaSettings(newBundle.core().arenaSettings());
        serviceRegistry.get(ArenaService.class).applyWatchdogSettings(newBundle.core().timeoutSettings().watchdog());
        serviceRegistry.get(BudgetService.class).applySettings(newBundle.core().arenaSettings());
        serviceRegistry.get(ProfileService.class).applyDegradedModeSettings(newBundle.core().degradedModeSettings());
        serviceRegistry.get(EconomyService.class).applyDegradedModeSettings(newBundle.core().degradedModeSettings());
        serviceRegistry.get(ShopService.class).applyCatalog(newBundle.economy().shop());
        serviceRegistry.get(HoloService.class).applySettings(newBundle.core().hologramSettings());
        serviceRegistry.get(HoloService.class).loadFromConfig();
        if (servicesExposed && !newBundle.core().serviceSettings().exposeBukkitServices()) {
            getServer().getServicesManager().unregisterAll(this);
            servicesExposed = false;
        } else if (!servicesExposed && newBundle.core().serviceSettings().exposeBukkitServices()) {
            maybeExposeServices();
        }
    }

    public void handleDump(CommandSender sender) {
        if (!sender.hasPermission("nexus.admin.dump")) {
            messageFacade.send(sender, "errors.no_permission");
            return;
        }
        messageFacade.send(sender, "admin.dump.header");
        List<Component> lines = DumpUtil.createDump(this, getServer(), bundle, executorManager, ringScheduler, dbProvider, serviceRegistry,
                serviceRegistry.get(BudgetService.class), serviceRegistry.get(WatchdogService.class), serviceRegistry.get(HoloService.class));
        lines.forEach(sender::sendMessage);
        messageFacade.send(sender, "admin.dump.success");
    }

    public void handleHologram(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexus.holo.manage")) {
            messageFacade.send(sender, "errors.no_permission");
            return;
        }
        if (args.length <= 1) {
            messageFacade.send(sender, "holograms.usage");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        HoloService holoService = serviceRegistry.get(HoloService.class);
        switch (action) {
            case "list" -> handleHologramList(sender, holoService);
            case "reload" -> {
                holoService.loadFromConfig();
                messageFacade.send(sender, "holograms.reload.ok");
            }
            case "create" -> handleHologramCreate(sender, args, holoService);
            case "move" -> handleHologramMove(sender, args, holoService);
            case "remove" -> handleHologramRemove(sender, args, holoService);
            default -> messageFacade.send(sender, "holograms.usage");
        }
    }

    private void handleHologramList(CommandSender sender, HoloService service) {
        List<Hologram> holograms = new ArrayList<>(service.holograms());
        if (holograms.isEmpty()) {
            messageFacade.send(sender, "holograms.list.empty");
            return;
        }
        messageFacade.send(sender, "holograms.list.header",
                Placeholder.unparsed("count", Integer.toString(holograms.size())));
        holograms.stream()
                .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
                .forEach(hologram -> {
                    Location location = hologram.location();
                    String world = location != null && location.getWorld() != null ? location.getWorld().getName() : "?";
                    messageFacade.send(sender, "holograms.list.entry",
                            Placeholder.unparsed("id", hologram.id()),
                            Placeholder.unparsed("world", world),
                            Placeholder.unparsed("x", formatCoordinate(location == null ? 0D : location.getX())),
                            Placeholder.unparsed("y", formatCoordinate(location == null ? 0D : location.getY())),
                            Placeholder.unparsed("z", formatCoordinate(location == null ? 0D : location.getZ())),
                            Placeholder.unparsed("lines", Integer.toString(hologram.lines().size())),
                            Placeholder.unparsed("group", hologram.group()));
                });
    }

    private void handleHologramCreate(CommandSender sender, String[] args, HoloService service) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            messageFacade.send(sender, "holograms.errors.player_only");
            return;
        }
        if (args.length < 5) {
            messageFacade.send(sender, "holograms.create.help");
            return;
        }
        String id = args[2];
        if (service.getHologram(id).isPresent()) {
            messageFacade.send(sender, "holograms.errors.duplicate", Placeholder.unparsed("id", id));
            return;
        }
        String group = args[3];
        String raw = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        List<String> lines = Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
        if (lines.isEmpty()) {
            messageFacade.send(sender, "holograms.errors.invalid_definition");
            return;
        }
        try {
            Hologram hologram = service.createHologram(id, player.getLocation(), lines);
            hologram.setGroup(group);
            messageFacade.send(sender, "holograms.created", Placeholder.unparsed("id", id));
        } catch (IllegalArgumentException exception) {
            messageFacade.send(sender, "holograms.errors.duplicate", Placeholder.unparsed("id", id));
        }
    }

    private void handleHologramMove(CommandSender sender, String[] args, HoloService service) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            messageFacade.send(sender, "holograms.errors.player_only");
            return;
        }
        if (args.length < 3) {
            messageFacade.send(sender, "holograms.usage");
            return;
        }
        String id = args[2];
        service.getHologram(id).ifPresentOrElse(hologram -> {
            hologram.teleport(player.getLocation());
            messageFacade.send(sender, "holograms.moved", Placeholder.unparsed("id", id));
        }, () -> messageFacade.send(sender, "holograms.errors.not_found", Placeholder.unparsed("id", id)));
    }

    private void handleHologramRemove(CommandSender sender, String[] args, HoloService service) {
        if (args.length < 3) {
            messageFacade.send(sender, "holograms.usage");
            return;
        }
        String id = args[2];
        if (service.getHologram(id).isEmpty()) {
            messageFacade.send(sender, "holograms.errors.not_found", Placeholder.unparsed("id", id));
            return;
        }
        service.removeHologram(id);
        messageFacade.send(sender, "holograms.removed", Placeholder.unparsed("id", id));
    }

    public List<String> suggestHolograms(String prefix) {
        HoloService service = serviceRegistry.get(HoloService.class);
        return service.holograms().stream()
                .map(Hologram::id)
                .filter(id -> id.startsWith(prefix))
                .sorted()
                .toList();
    }

    public List<String> suggestAdminPlayerTargets(String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(Objects::nonNull)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted()
                .toList();
    }

    public List<String> suggestImportFiles(String prefix) {
        if (playerDataImporter == null) {
            return List.of();
        }
        return playerDataImporter.suggestAvailableFiles(prefix);
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public void handleBudget(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexus.admin.budget")) {
            messageFacade.send(sender, "errors.no_permission");
            return;
        }
        BudgetService budgetService = serviceRegistry.get(BudgetService.class);
        if (args.length <= 1) {
            sendBudgetSummary(sender, budgetService);
            return;
        }
        UUID arenaId;
        try {
            arenaId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Identifiant d'arène invalide.", NamedTextColor.RED));
            return;
        }
        budgetService.getSnapshot(arenaId).ifPresentOrElse(snapshot -> {
            messageFacade.prefix(sender).ifPresent(sender::sendMessage);
            sender.sendMessage(Component.text("=== Budget pour l'arène " + snapshot.arenaId() + " ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Map: " + snapshot.mapId() + " | Mode: " + snapshot.mode(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Entités: " + snapshot.entities() + " / " + snapshot.maxEntities()
                    + formatPending(snapshot.pendingEntities()), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Items au sol: " + snapshot.items() + " / " + snapshot.maxItems()
                    + formatPending(snapshot.pendingItems()), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Projectiles: " + snapshot.projectiles() + " / " + snapshot.maxProjectiles()
                    + formatPending(snapshot.pendingProjectiles()), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Particules (tick): " + snapshot.particles() + " / "
                    + snapshot.particlesSoftCap() + " (Soft) / " + snapshot.particlesHardCap() + " (Hard)", NamedTextColor.YELLOW));
        }, () -> sender.sendMessage(Component.text("Arène non trouvée.", NamedTextColor.RED)));
    }

    public void handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendAdminUsage(sender);
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "player" -> handleAdminPlayer(sender, args);
            case "audit" -> handleAdminAudit(sender, args);
            default -> sendAdminUsage(sender);
        }
    }

    private void handleAdminPlayer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexus.admin.player.export") && !sender.hasPermission("nexus.admin.player.import")) {
            messageFacade.send(sender, "errors.no_permission");
            return;
        }
        if (args.length < 4) {
            sendAdminPlayerUsage(sender);
            return;
        }
        ResolvedPlayer target = resolvePlayer(args[2]);
        if (target == null) {
            messageFacade.send(sender, "admin.player.invalid_target", Placeholder.unparsed("input", args[2]));
            return;
        }
        String action = args[3].toLowerCase(Locale.ROOT);
        switch (action) {
            case "export" -> handlePlayerExport(sender, target, args);
            case "import" -> handlePlayerImport(sender, target, args);
            default -> sendAdminPlayerUsage(sender);
        }
    }

    private void handleAdminAudit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexus.admin.audit.view")) {
            messageFacade.send(sender, "errors.no_permission");
            return;
        }
        if (auditService == null) {
            messageFacade.send(sender, "admin.audit.unavailable");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("log")) {
            sendAdminAuditUsage(sender);
            return;
        }
        if (args.length < 4) {
            sendAdminAuditUsage(sender);
            return;
        }
        String token = args[3];
        boolean global = token.equals("*") || token.equalsIgnoreCase("all");
        ResolvedPlayer resolved = global ? null : resolvePlayer(token);
        int page = 1;
        if (args.length >= 5) {
            try {
                page = Integer.parseInt(args[4]);
            } catch (NumberFormatException exception) {
                messageFacade.send(sender, "admin.audit.invalid_page", Placeholder.unparsed("page", args[4]));
                return;
            }
            if (page <= 0) {
                messageFacade.send(sender, "admin.audit.invalid_page", Placeholder.unparsed("page", args[4]));
                return;
            }
        }
        Optional<UUID> subjectUuid = Optional.empty();
        Optional<String> subjectName = Optional.empty();
        String scopeLabel;
        String commandToken;
        if (global) {
            scopeLabel = "toutes les actions";
            commandToken = "*";
        } else if (resolved != null) {
            scopeLabel = resolved.displayName();
            commandToken = resolved.displayName();
            subjectUuid = Optional.of(resolved.uuid());
            subjectName = optionalAuditName(resolved.displayName());
        } else {
            String trimmed = token.trim();
            scopeLabel = trimmed.isEmpty() ? token : trimmed;
            commandToken = scopeLabel;
            subjectName = optionalAuditName(scopeLabel);
        }
        AuditLogQuery query = new AuditLogQuery(page, AUDIT_LOG_PAGE_SIZE, subjectUuid, subjectName);
        executorManager.thenMain(auditService.query(query), auditPage ->
                        sendAuditLogResults(sender, scopeLabel, commandToken, auditPage))
                .exceptionally(throwable -> {
                    Throwable cause = unwrap(throwable);
                    logger.warn("Impossible de récupérer les logs d'audit", cause);
                    messageFacade.send(sender, "admin.audit.error",
                            Placeholder.unparsed("reason", describeError(cause)));
                    return null;
                });
    }

    private void handlePlayerExport(CommandSender sender, ResolvedPlayer target, String[] args) {
        if (!sender.hasPermission("nexus.admin.player.export")) {
            messageFacade.send(sender, "errors.no_permission");
            return;
        }
        if (playerDataExporter == null || exportsDirectory == null) {
            messageFacade.send(sender, "admin.player.unavailable");
            return;
        }
        PlayerDataFormat format = PlayerDataFormat.JSON;
        if (args.length >= 5) {
            String option = args[4];
            if (option.startsWith("--format=")) {
                option = option.substring("--format=".length());
            }
            try {
                format = PlayerDataFormat.fromToken(option);
            } catch (IllegalArgumentException exception) {
                messageFacade.send(sender, "admin.player.export.invalid_format", Placeholder.unparsed("format", option));
                return;
            }
        }
        String commandLine = "/nexus " + String.join(" ", args);
        logAdminCommand(sender, AuditActionType.ADMIN_COMMAND, target,
                "command=" + commandLine
                        + "; action=export; format=" + format.name().toLowerCase(Locale.ROOT));
        messageFacade.send(sender, "admin.player.export.start",
                Placeholder.unparsed("player", target.displayName()),
                Placeholder.unparsed("uuid", target.uuid().toString()),
                Placeholder.unparsed("format", format.name().toLowerCase(Locale.ROOT)));
        CompletableFuture<Path> future = playerDataExporter.exportPlayerData(target.uuid(), target.displayName(), format);
        executorManager.thenMain(future, path -> messageFacade.send(sender, "admin.player.export.success",
                        Placeholder.unparsed("player", target.displayName()),
                        Placeholder.unparsed("path", formatRelativePath(path))))
                .exceptionally(throwable -> {
                    Throwable cause = unwrap(throwable);
                    logger.warn("Export des données joueur impossible pour " + target.uuid(), cause);
                    messageFacade.send(sender, "admin.player.export.error",
                            Placeholder.unparsed("player", target.displayName()),
                            Placeholder.unparsed("reason", describeError(cause)));
                    return null;
                });
    }

    private void handlePlayerImport(CommandSender sender, ResolvedPlayer target, String[] args) {
        if (!sender.hasPermission("nexus.admin.player.import")) {
            messageFacade.send(sender, "errors.no_permission");
            return;
        }
        if (playerDataImporter == null || importsDirectory == null) {
            messageFacade.send(sender, "admin.player.unavailable");
            return;
        }
        if (args.length < 5) {
            sendAdminPlayerUsage(sender);
            return;
        }
        String fileName = args[4];
        String actorKey = senderKey(sender);
        if (args.length >= 6 && args[5].equalsIgnoreCase("confirm")) {
            String commandLine = "/nexus " + String.join(" ", args);
            logAdminCommand(sender, AuditActionType.ADMIN_COMMAND, target,
                    "command=" + commandLine + "; action=import-confirm; file=" + fileName);
            messageFacade.send(sender, "admin.player.import.start",
                    Placeholder.unparsed("player", target.displayName()),
                    Placeholder.unparsed("file", fileName));
            CompletableFuture<Void> future = playerDataImporter.confirmImport(actorKey, target.uuid(), fileName);
            executorManager.thenMain(future, unused -> messageFacade.send(sender, "admin.player.import.success",
                            Placeholder.unparsed("player", target.displayName())))
                    .exceptionally(throwable -> {
                        handleImportConfirmationError(sender, target, fileName, throwable);
                        return null;
                    });
            return;
        }
        CompletableFuture<ImportPreparation> future = playerDataImporter.prepareImport(actorKey, target.uuid(), fileName);
        String commandLine = "/nexus " + String.join(" ", args);
        logAdminCommand(sender, AuditActionType.ADMIN_COMMAND, target,
                "command=" + commandLine + "; action=import-preview; file=" + fileName);
        executorManager.thenMain(future, preparation -> {
                    PlayerProfileSnapshot profile = preparation.snapshot().profile();
                    PlayerEconomySnapshot economy = preparation.snapshot().economy();
                    messageFacade.send(sender, "admin.player.import.preview.header",
                            Placeholder.unparsed("player", target.displayName()),
                            Placeholder.unparsed("file", preparation.fileName()));
                    messageFacade.send(sender, "admin.player.import.preview.target",
                            Placeholder.unparsed("uuid", target.uuid().toString()),
                            Placeholder.unparsed("current_balance", formatLong(preparation.currentEconomy().balance())),
                            Placeholder.unparsed("balance", formatLong(economy.balance())));
                    messageFacade.send(sender, "admin.player.import.preview.profile",
                            Placeholder.unparsed("elo", formatLong(profile.eloRating())),
                            Placeholder.unparsed("kills", formatLong(profile.totalKills())),
                            Placeholder.unparsed("deaths", formatLong(profile.totalDeaths())),
                            Placeholder.unparsed("wins", formatLong(profile.totalWins())),
                            Placeholder.unparsed("losses", formatLong(profile.totalLosses())),
                            Placeholder.unparsed("matches", formatLong(profile.matchesPlayed())));
                    messageFacade.send(sender, "admin.player.import.preview.confirm",
                            Placeholder.unparsed("player", target.displayName()),
                            Placeholder.unparsed("file", preparation.fileName()));
                })
                .exceptionally(throwable -> {
                    handleImportPreparationError(sender, fileName, throwable);
                    return null;
                });
    }

    private void sendBudgetSummary(CommandSender sender, BudgetService budgetService) {
        Collection<BudgetSnapshot> snapshots = budgetService.snapshots();
        messageFacade.prefix(sender).ifPresent(sender::sendMessage);
        sender.sendMessage(Component.text("=== Budgets actifs ===", NamedTextColor.GOLD));
        if (snapshots.isEmpty()) {
            sender.sendMessage(Component.text("Aucune arène active.", NamedTextColor.GRAY));
            return;
        }
        snapshots.stream()
                .sorted((left, right) -> left.arenaId().compareTo(right.arenaId()))
                .forEach(snapshot -> sender.sendMessage(Component.text(
                        "• " + snapshot.arenaId()
                                + " -> Entités: " + snapshot.entities() + "/" + snapshot.maxEntities()
                                + ", Items: " + snapshot.items() + "/" + snapshot.maxItems()
                                + ", Projectiles: " + snapshot.projectiles() + "/" + snapshot.maxProjectiles()
                                + ", Particules: " + snapshot.particles() + "/" + snapshot.particlesSoftCap()
                                + " (Soft)", NamedTextColor.GRAY)));
    }

    private String formatPending(long pending) {
        if (pending <= 0L) {
            return "";
        }
        return " (en attente: " + pending + ")";
    }

    private void sendAdminPlayerUsage(CommandSender sender) {
        messageFacade.send(sender, "admin.player.usage");
    }

    private void sendAdminAuditUsage(CommandSender sender) {
        messageFacade.send(sender, "admin.audit.usage");
    }

    private void sendAdminUsage(CommandSender sender) {
        sendAdminPlayerUsage(sender);
        sendAdminAuditUsage(sender);
    }

    private Optional<String> optionalAuditName(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(trimmed.length() > 16 ? trimmed.substring(0, 16) : trimmed);
    }

    private void sendAuditLogResults(CommandSender sender,
                                     String scopeLabel,
                                     String commandToken,
                                     AuditService.AuditLogPage page) {
        messageFacade.prefix(sender).ifPresent(sender::sendMessage);
        messageFacade.send(sender, "admin.audit.header",
                Placeholder.unparsed("scope", scopeLabel),
                Placeholder.unparsed("page", Integer.toString(page.page())));
        if (page.entries().isEmpty()) {
            messageFacade.send(sender, "admin.audit.empty");
        } else {
            for (AuditLogRecord record : page.entries()) {
                messageFacade.send(sender, "admin.audit.entry",
                        Placeholder.unparsed("id", Long.toString(record.id())),
                        Placeholder.unparsed("timestamp", auditTimestampFormatter.format(record.timestamp())),
                        Placeholder.unparsed("type", record.actionType().displayName()),
                        Placeholder.unparsed("actor", formatAuditSubject(record.actorUuid(), record.actorName(), "Système")),
                        Placeholder.unparsed("target", formatAuditSubject(record.targetUuid(), record.targetName(), "-")),
                        Placeholder.unparsed("details", truncate(record.details(), AUDIT_DETAILS_MAX_LENGTH)));
            }
        }
        if (page.hasNext()) {
            int nextPage = page.page() + 1;
            String command = "/nexus admin audit log " + commandToken + " " + nextPage;
            messageFacade.send(sender, "admin.audit.more", Placeholder.unparsed("command", command));
        }
    }

    private String formatAuditSubject(UUID uuid, String name, String defaultValue) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (uuid != null) {
            return uuid.toString();
        }
        return defaultValue;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void logAdminCommand(CommandSender sender, AuditActionType actionType, ResolvedPlayer target, String details) {
        if (auditService == null) {
            return;
        }
        AuditActor actor = resolveAuditActor(sender);
        auditService.log(new AuditEntry(
                actor.uuid(),
                actor.name(),
                actionType,
                target != null ? target.uuid() : null,
                target != null ? target.displayName() : null,
                details));
    }

    private AuditActor resolveAuditActor(CommandSender sender) {
        if (sender instanceof Player player) {
            return new AuditActor(player.getUniqueId(), player.getName());
        }
        return new AuditActor(null, sender.getName());
    }

    private ResolvedPlayer resolvePlayer(String token) {
        try {
            UUID uuid = UUID.fromString(token);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String name = offline.getName();
            return new ResolvedPlayer(uuid, name != null ? name : uuid.toString());
        } catch (IllegalArgumentException ignored) {
            // Continue with name-based resolution
        }
        Player online = getServer().getPlayerExact(token);
        if (online != null) {
            return new ResolvedPlayer(online.getUniqueId(), online.getName());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(token);
        if (cached == null) {
            cached = Bukkit.getOfflinePlayer(token);
        }
        if (cached != null && cached.getUniqueId() != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            String name = cached.getName() != null ? cached.getName() : token;
            return new ResolvedPlayer(cached.getUniqueId(), name);
        }
        return null;
    }

    private String senderKey(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUniqueId().toString();
        }
        return "console";
    }

    private void handleImportPreparationError(CommandSender sender, String fileName, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof PlayerDataValidationException validationException) {
            messageFacade.send(sender, "admin.player.import.invalid",
                    Placeholder.unparsed("reason", describeError(validationException)));
            return;
        }
        if (cause instanceof IOException ioException) {
            messageFacade.send(sender, "admin.player.import.error",
                    Placeholder.unparsed("reason", describeError(ioException)));
            return;
        }
        logger.warn("Préparation d'import impossible pour " + fileName, cause);
        messageFacade.send(sender, "admin.player.import.error",
                Placeholder.unparsed("reason", describeError(cause)));
    }

    private void handleImportConfirmationError(CommandSender sender, ResolvedPlayer target, String fileName, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof IllegalStateException stateException) {
            String code = stateException.getMessage();
            if ("no-pending".equals(code)) {
                messageFacade.send(sender, "admin.player.import.none");
                return;
            }
            if ("mismatch".equals(code)) {
                messageFacade.send(sender, "admin.player.import.mismatch");
                return;
            }
            if ("expired".equals(code)) {
                messageFacade.send(sender, "admin.player.import.expired");
                return;
            }
        }
        if (cause instanceof PlayerDataValidationException validationException) {
            messageFacade.send(sender, "admin.player.import.invalid",
                    Placeholder.unparsed("reason", describeError(validationException)));
            return;
        }
        if (cause instanceof IOException ioException) {
            messageFacade.send(sender, "admin.player.import.error",
                    Placeholder.unparsed("reason", describeError(ioException)));
            return;
        }
        logger.warn("Import des données joueur impossible pour " + target.uuid(), cause);
        messageFacade.send(sender, "admin.player.import.error",
                Placeholder.unparsed("reason", describeError(cause)));
    }

    private String formatLong(long value) {
        return Long.toString(value);
    }

    private String formatRelativePath(Path path) {
        if (path == null) {
            return "";
        }
        try {
            Path dataPath = getDataFolder().toPath();
            if (path.startsWith(dataPath)) {
                return dataPath.relativize(path).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
            // Fallback to absolute path below
        }
        return path.toAbsolutePath().toString();
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        if (throwable instanceof ExecutionException executionException && executionException.getCause() != null) {
            return unwrap(executionException.getCause());
        }
        return throwable;
    }

    private String describeError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private record ResolvedPlayer(UUID uuid, String displayName) {
    }

    private record AuditActor(UUID uuid, String name) {
    }

    public MessageFacade messages() {
        return messageFacade;
    }

    public ConfigBundle bundle() {
        return bundle;
    }

    private String formatIssue(ReloadReport.ValidationMessage message) {
        return "[%s] %s -> %s".formatted(message.file(), message.path(), message.message());
    }

    private Component formatIssue(ReloadReport.ValidationMessage message, NamedTextColor color) {
        return Component.text(" • [" + message.file() + "] " + message.path() + " -> " + message.message(), color);
    }

    private void registerSingletons() {
        serviceRegistry.registerSingleton(JavaPlugin.class, this);
        serviceRegistry.registerSingleton(NexusPlugin.class, this);
        serviceRegistry.registerSingleton(NexusLogger.class, logger);
        serviceRegistry.registerSingleton(ConfigManager.class, configManager);
        serviceRegistry.registerSingleton(ConfigBundle.class, bundle);
        serviceRegistry.registerSingleton(CoreConfig.class, bundle.core());
        serviceRegistry.registerSingleton(EconomyConfig.class, bundle.economy());
        serviceRegistry.registerSingleton(ExecutorManager.class, executorManager);
        serviceRegistry.registerSingleton(DbProvider.class, dbProvider);
        serviceRegistry.registerSingleton(DatabaseMigrator.class, databaseMigrator);
        serviceRegistry.registerSingleton(Boolean.class, placeholderApiAvailable);
    }

    private void setupEconomy() {
        PluginManager manager = getServer().getPluginManager();
        if (!isPluginAvailable(manager, "Vault")) {
            logger.warn("Vault non trouvé. Utilisation du système d'économie interne de Nexus (non persistant).");
            serviceRegistry.registerService(EconomyService.class, EconomyServiceImpl.class);
            return;
        }

        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            logger.warn("Vault est installé, mais aucun fournisseur d'économie n'a été trouvé. Utilisation du système interne.");
            serviceRegistry.registerService(EconomyService.class, EconomyServiceImpl.class);
            return;
        }

        Economy provider = registration.getProvider();
        serviceRegistry.registerSingleton(Economy.class, provider);
        serviceRegistry.registerService(EconomyService.class, VaultEconomyService.class);
        logger.info("Vault détecté et connecté ! L'économie sera gérée par Vault.");
    }

    private void setupPermissions() {
        PluginManager manager = getServer().getPluginManager();
        if (!isPluginAvailable(manager, "LuckPerms")) {
            logger.warn("LuckPerms non trouvé. Les fonctionnalités de permissions avancées seront désactivées.");
            contextManager = new NexusContextManager(this, logger, null);
            serviceRegistry.registerSingleton(NexusContextManager.class, contextManager);
            return;
        }

        try {
            this.luckPermsApi = LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            logger.warn("LuckPerms détecté mais l'API n'est pas disponible. Les fonctionnalités de permissions avancées seront désactivées.");
            contextManager = new NexusContextManager(this, logger, null);
            serviceRegistry.registerSingleton(NexusContextManager.class, contextManager);
            return;
        }

        serviceRegistry.registerSingleton(LuckPerms.class, luckPermsApi);
        contextManager = new NexusContextManager(this, logger, luckPermsApi);
        serviceRegistry.registerSingleton(NexusContextManager.class, contextManager);
        logger.info("LuckPerms détecté et connecté !");
    }

    private void registerServices() {
        serviceRegistry.registerService(RingScheduler.class, RingScheduler.class);
        serviceRegistry.registerService(MapService.class, MapServiceImpl.class);
        serviceRegistry.registerService(ProfileRepository.class, ProfileRepositoryImpl.class);
        serviceRegistry.registerService(PlayerClassRepository.class, PlayerClassRepositoryImpl.class);
        serviceRegistry.registerService(PlayerCosmeticRepository.class, PlayerCosmeticRepositoryImpl.class);
        serviceRegistry.registerService(EconomyRepository.class, EconomyRepositoryImpl.class);
        serviceRegistry.registerService(MatchRepository.class, MatchRepositoryImpl.class);
        serviceRegistry.registerService(DataPurgeService.class, DataPurgeService.class);
        serviceRegistry.registerService(RewardClaimRepository.class, RewardClaimRepositoryImpl.class);
        serviceRegistry.registerService(AuditLogRepository.class, AuditLogRepositoryImpl.class);
        serviceRegistry.registerService(AuditService.class, AuditServiceImpl.class);
        serviceRegistry.registerService(PersistenceService.class, PersistenceServiceImpl.class);
        serviceRegistry.registerService(ProfileService.class, ProfileServiceImpl.class);
        serviceRegistry.registerService(QueueService.class, QueueServiceImpl.class);
        serviceRegistry.registerService(TimerService.class, TimerServiceImpl.class);
        serviceRegistry.registerService(ShopService.class, ShopServiceImpl.class);
        serviceRegistry.registerService(BudgetService.class, BudgetServiceImpl.class);
        serviceRegistry.registerService(WatchdogService.class, WatchdogServiceImpl.class);
        serviceRegistry.registerService(AnalyticsRepository.class, AnalyticsRepository.class);
        serviceRegistry.registerService(AnalyticsService.class, AnalyticsService.class);
        serviceRegistry.registerService(DailyStatsRepository.class, DailyStatsRepository.class);
        serviceRegistry.registerService(DailyStatsAggregatorService.class, DailyStatsAggregatorService.class);
        serviceRegistry.registerService(ArenaService.class, ArenaServiceImpl.class);
        serviceRegistry.registerService(HoloService.class, HoloServiceImpl.class);
        serviceRegistry.registerService(RewardService.class, RewardServiceImpl.class);
    }

    private void maybeExposeServices() {
        if (!bundle.core().serviceSettings().exposeBukkitServices()) {
            return;
        }
        ServicesManager servicesManager = getServer().getServicesManager();
        servicesManager.register(ArenaService.class, serviceRegistry.get(ArenaService.class), this, ServicePriority.Normal);
        servicesManager.register(MapService.class, serviceRegistry.get(MapService.class), this, ServicePriority.Normal);
        servicesManager.register(QueueService.class, serviceRegistry.get(QueueService.class), this, ServicePriority.Normal);
        servicesManager.register(EconomyService.class, serviceRegistry.get(EconomyService.class), this, ServicePriority.Normal);
        servicesManager.register(ProfileService.class, serviceRegistry.get(ProfileService.class), this, ServicePriority.Normal);
        servicesManager.register(BudgetService.class, serviceRegistry.get(BudgetService.class), this, ServicePriority.Normal);
        servicesManager.register(HoloService.class, serviceRegistry.get(HoloService.class), this, ServicePriority.Normal);
        servicesExposed = true;
    }

    public List<String> suggestBudgetArenas(String prefix) {
        BudgetService budgetService = serviceRegistry.get(BudgetService.class);
        return budgetService.snapshots().stream()
                .map(snapshot -> snapshot.arenaId().toString())
                .filter(id -> id.startsWith(prefix))
                .sorted()
                .toList();
    }
}