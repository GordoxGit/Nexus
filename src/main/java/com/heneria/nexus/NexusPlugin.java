package com.heneria.nexus;

import com.heneria.nexus.analytics.AnalyticsRepository;
import com.heneria.nexus.analytics.AnalyticsService;
import com.heneria.nexus.budget.BudgetService;
import com.heneria.nexus.budget.BudgetServiceImpl;
import com.heneria.nexus.budget.BudgetSnapshot;
import com.heneria.nexus.command.NexusCommand;
import com.heneria.nexus.config.ConfigBundle;
import com.heneria.nexus.config.ConfigManager;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.config.ReloadReport;
import com.heneria.nexus.db.DatabaseMigrator;
import com.heneria.nexus.db.DbProvider;
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
import com.heneria.nexus.api.QueueService;
import com.heneria.nexus.api.service.TimerService;
import com.heneria.nexus.service.core.ArenaServiceImpl;
import com.heneria.nexus.service.core.EconomyServiceImpl;
import com.heneria.nexus.service.core.MapServiceImpl;
import com.heneria.nexus.service.core.ProfileServiceImpl;
import com.heneria.nexus.service.core.QueueServiceImpl;
import com.heneria.nexus.service.core.TimerServiceImpl;
import com.heneria.nexus.service.core.VaultEconomyService;
import com.heneria.nexus.service.permissions.NexusContextManager;
import com.heneria.nexus.util.DumpUtil;
import com.heneria.nexus.util.MessageFacade;
import com.heneria.nexus.util.NexusLogger;
import com.heneria.nexus.watchdog.WatchdogService;
import com.heneria.nexus.watchdog.WatchdogServiceImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.Location;
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
        configureDatabase(newBundle.core().databaseSettings());
        serviceRegistry.get(QueueService.class).applySettings(newBundle.core().queueSettings());
        serviceRegistry.get(ArenaService.class).applyArenaSettings(newBundle.core().arenaSettings());
        serviceRegistry.get(ArenaService.class).applyWatchdogSettings(newBundle.core().timeoutSettings().watchdog());
        serviceRegistry.get(BudgetService.class).applySettings(newBundle.core().arenaSettings());
        serviceRegistry.get(ProfileService.class).applyDegradedModeSettings(newBundle.core().degradedModeSettings());
        serviceRegistry.get(EconomyService.class).applyDegradedModeSettings(newBundle.core().degradedModeSettings());
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
        serviceRegistry.registerService(ProfileService.class, ProfileServiceImpl.class);
        serviceRegistry.registerService(QueueService.class, QueueServiceImpl.class);
        serviceRegistry.registerService(TimerService.class, TimerServiceImpl.class);
        serviceRegistry.registerService(BudgetService.class, BudgetServiceImpl.class);
        serviceRegistry.registerService(WatchdogService.class, WatchdogServiceImpl.class);
        serviceRegistry.registerService(AnalyticsRepository.class, AnalyticsRepository.class);
        serviceRegistry.registerService(AnalyticsService.class, AnalyticsService.class);
        serviceRegistry.registerService(ArenaService.class, ArenaServiceImpl.class);
        serviceRegistry.registerService(HoloService.class, HoloServiceImpl.class);
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