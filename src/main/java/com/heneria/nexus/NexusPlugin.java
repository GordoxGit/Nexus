package com.heneria.nexus;

import com.heneria.nexus.command.NexusCommand;
import com.heneria.nexus.config.ConfigBundle;
import com.heneria.nexus.config.ConfigManager;
import com.heneria.nexus.config.NexusConfig;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.scheduler.GamePhase;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.scheduler.RingScheduler.TaskProfile;
import com.heneria.nexus.service.ExecutorPools;
import com.heneria.nexus.service.ServiceRegistry;
import com.heneria.nexus.service.api.ArenaService;
import com.heneria.nexus.service.api.EconomyService;
import com.heneria.nexus.service.api.MapService;
import com.heneria.nexus.service.api.ProfileService;
import com.heneria.nexus.service.api.QueueService;
import com.heneria.nexus.service.core.ArenaServiceImpl;
import com.heneria.nexus.service.core.EconomyServiceImpl;
import com.heneria.nexus.service.core.MapServiceImpl;
import com.heneria.nexus.service.core.ProfileServiceImpl;
import com.heneria.nexus.service.core.QueueServiceImpl;
import com.heneria.nexus.util.DumpUtil;
import com.heneria.nexus.util.MessageFacade;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for the Nexus plugin.
 */
public final class NexusPlugin extends JavaPlugin {

    private static final String LOG_PREFIX = "[NEXUS] ";

    private NexusLogger logger;
    private ConfigManager configManager;
    private ConfigBundle bundle;
    private MessageFacade messageFacade;
    private ServiceRegistry serviceRegistry;
    private ExecutorPools executorPools;
    private RingScheduler ringScheduler;
    private DbProvider dbProvider;
    private boolean bootstrapFailed;
    private boolean servicesExposed;

    @Override
    public void onLoad() {
        this.logger = new NexusLogger(getLogger(), LOG_PREFIX);
        this.configManager = new ConfigManager(this, logger);
        ConfigManager.LoadResult loadResult = configManager.initialLoad();
        if (!loadResult.success()) {
            loadResult.errors().forEach(error -> logger.error("Erreur de configuration: " + error));
            bootstrapFailed = true;
            return;
        }
        this.bundle = loadResult.bundle();
        this.messageFacade = new MessageFacade(bundle.messages(), logger);
        this.executorPools = new ExecutorPools(logger, bundle.config().threadSettings());
        this.serviceRegistry = new ServiceRegistry(logger);
        registerSingletons();
        registerServices();
        try {
            serviceRegistry.wire(Duration.ofMillis(bundle.config().timeoutSettings().startMs()));
            this.ringScheduler = serviceRegistry.get(RingScheduler.class);
            this.dbProvider = serviceRegistry.get(DbProvider.class);
            ringScheduler.applyPerfSettings(bundle.config().arenaSettings());
        } catch (Exception exception) {
            logger.error("Impossible d'initialiser le registre de services", exception);
            bootstrapFailed = true;
        }
    }

    @Override
    public void onEnable() {
        if (bootstrapFailed) {
            logger.error("Initialisation de Nexus impossible, désactivation");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.logger = new NexusLogger(getLogger(), LOG_PREFIX);
        this.messageFacade.update(bundle.messages());
        registerCommands();
        logEnvironment();
        configureDatabase(bundle.config().databaseSettings());
        try {
            serviceRegistry.startAll(Duration.ofMillis(bundle.config().timeoutSettings().startMs()));
            ringScheduler.registerProfile(TaskProfile.HUD,
                    EnumSet.of(GamePhase.LOBBY, GamePhase.STARTING, GamePhase.PLAYING),
                    () -> {
                    });
            ringScheduler.registerProfile(TaskProfile.SCOREBOARD,
                    EnumSet.of(GamePhase.STARTING, GamePhase.PLAYING, GamePhase.RESET),
                    () -> {
                    });
            maybeExposeServices();
        } catch (Exception exception) {
            logger.error("Démarrage des services impossible", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (servicesExposed) {
            getServer().getServicesManager().unregisterAll(this);
            servicesExposed = false;
        }
        if (serviceRegistry != null) {
            serviceRegistry.stopAll(Duration.ofMillis(bundle != null ? bundle.config().timeoutSettings().stopMs() : 3000L));
        }
        if (executorPools != null) {
            executorPools.close();
        }
        logger.info("Nexus désactivé proprement");
    }

    private void configureDatabase(NexusConfig.DatabaseSettings settings) {
        ExecutorService ioExecutor = executorPools.ioExecutor();
        CompletableFuture<Boolean> future = dbProvider.applyConfiguration(settings, ioExecutor);
        try {
            boolean success = future.orTimeout(bundle.config().timeoutSettings().startMs(), TimeUnit.MILLISECONDS).join();
            if (!success && bundle.config().degradedModeSettings().enabled()) {
                logger.warn("Mode dégradé activé : MariaDB indisponible");
            }
        } catch (Exception exception) {
            logger.warn("Configuration MariaDB impossible", exception);
        }
    }

    private void registerCommands() {
        PluginCommand command = Objects.requireNonNull(getCommand("nexus"), "Command nexus not registered in plugin.yml");
        NexusCommand executor = new NexusCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void logEnvironment() {
        logger.info("Démarrage de Nexus %s".formatted(getDescription().getVersion()));
        logger.info("Java: %s".formatted(System.getProperty("java.version")));
        logger.info("Paper: %s".formatted(getServer().getVersion()));
        logger.info("Fuseau horaire: %s".formatted(bundle.config().timezone()));
        logger.info("HUD: %d Hz, scoreboard: %d Hz".formatted(
                bundle.config().arenaSettings().hudHz(),
                bundle.config().arenaSettings().scoreboardHz()));
        logger.info("Matchmaking: %d Hz".formatted(bundle.config().queueSettings().tickHz()));
        logger.info("Threads: io=%d compute=%d".formatted(
                bundle.config().threadSettings().ioPool(),
                bundle.config().threadSettings().computePool()));
    }

    public void sendHelp(CommandSender sender) {
        messageFacade.send(sender, "commands.help.header");
        messageFacade.send(sender, "commands.help.help");
        if (sender.hasPermission("nexus.admin.reload")) {
            messageFacade.send(sender, "commands.help.reload");
        }
        if (sender.hasPermission("nexus.admin.dump")) {
            messageFacade.send(sender, "commands.help.dump");
        }
    }

    public void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nexus.admin.reload")) {
            messageFacade.send(sender, "commands.errors.no-permission");
            return;
        }
        messageFacade.send(sender, "commands.reload.start");
        ConfigManager.LoadResult reload = configManager.reloadFromDisk();
        if (!reload.success()) {
            messageFacade.send(sender, "commands.errors.invalid-config");
            reload.errors().forEach(error -> sender.sendMessage(Component.text(" • " + error, NamedTextColor.RED)));
            return;
        }
        ConfigBundle previous = this.bundle;
        try {
            applyBundle(reload.bundle());
            messageFacade.send(sender, "commands.reload.success");
        } catch (Exception exception) {
            this.bundle = previous;
            configManager.applyBundle(previous);
            messageFacade.send(sender, "commands.reload.failure");
            logger.error("Erreur lors du rechargement", exception);
        }
    }

    private synchronized void applyBundle(ConfigBundle newBundle) {
        executorPools.reconfigure(newBundle.config().threadSettings());
        ringScheduler.applyPerfSettings(newBundle.config().arenaSettings());
        messageFacade.update(newBundle.messages());
        this.bundle = newBundle;
        configManager.applyBundle(newBundle);
        serviceRegistry.updateSingleton(ConfigBundle.class, newBundle);
        serviceRegistry.updateSingleton(NexusConfig.class, newBundle.config());
        configureDatabase(newBundle.config().databaseSettings());
        serviceRegistry.get(QueueService.class).applySettings(newBundle.config().queueSettings());
        serviceRegistry.get(ArenaService.class).applyArenaSettings(newBundle.config().arenaSettings());
        serviceRegistry.get(ProfileService.class).applyDegradedModeSettings(newBundle.config().degradedModeSettings());
        serviceRegistry.get(EconomyService.class).applyDegradedModeSettings(newBundle.config().degradedModeSettings());
        if (servicesExposed && !newBundle.config().serviceSettings().exposeBukkitServices()) {
            getServer().getServicesManager().unregisterAll(this);
            servicesExposed = false;
        } else if (!servicesExposed && newBundle.config().serviceSettings().exposeBukkitServices()) {
            maybeExposeServices();
        }
    }

    public void handleDump(CommandSender sender) {
        if (!sender.hasPermission("nexus.admin.dump")) {
            messageFacade.send(sender, "commands.errors.no-permission");
            return;
        }
        messageFacade.send(sender, "commands.dump.header");
        List<Component> lines = DumpUtil.createDump(getServer(), bundle, executorPools, ringScheduler, dbProvider, serviceRegistry);
        lines.forEach(sender::sendMessage);
        messageFacade.send(sender, "commands.dump.success");
    }

    public MessageFacade messages() {
        return messageFacade;
    }

    public ConfigBundle bundle() {
        return bundle;
    }

    private void registerSingletons() {
        serviceRegistry.registerSingleton(JavaPlugin.class, this);
        serviceRegistry.registerSingleton(NexusPlugin.class, this);
        serviceRegistry.registerSingleton(NexusLogger.class, logger);
        serviceRegistry.registerSingleton(ConfigManager.class, configManager);
        serviceRegistry.registerSingleton(ConfigBundle.class, bundle);
        serviceRegistry.registerSingleton(NexusConfig.class, bundle.config());
        serviceRegistry.registerSingleton(ExecutorPools.class, executorPools);
    }

    private void registerServices() {
        serviceRegistry.registerService(DbProvider.class, DbProvider.class);
        serviceRegistry.registerService(RingScheduler.class, RingScheduler.class);
        serviceRegistry.registerService(MapService.class, MapServiceImpl.class);
        serviceRegistry.registerService(ProfileService.class, ProfileServiceImpl.class);
        serviceRegistry.registerService(EconomyService.class, EconomyServiceImpl.class);
        serviceRegistry.registerService(QueueService.class, QueueServiceImpl.class);
        serviceRegistry.registerService(ArenaService.class, ArenaServiceImpl.class);
    }

    private void maybeExposeServices() {
        if (!bundle.config().serviceSettings().exposeBukkitServices()) {
            return;
        }
        ServicesManager servicesManager = getServer().getServicesManager();
        servicesManager.register(ArenaService.class, serviceRegistry.get(ArenaService.class), this, ServicePriority.Normal);
        servicesManager.register(MapService.class, serviceRegistry.get(MapService.class), this, ServicePriority.Normal);
        servicesManager.register(QueueService.class, serviceRegistry.get(QueueService.class), this, ServicePriority.Normal);
        servicesManager.register(EconomyService.class, serviceRegistry.get(EconomyService.class), this, ServicePriority.Normal);
        servicesManager.register(ProfileService.class, serviceRegistry.get(ProfileService.class), this, ServicePriority.Normal);
        servicesExposed = true;
    }
}
