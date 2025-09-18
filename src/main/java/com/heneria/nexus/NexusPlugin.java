package com.heneria.nexus;

import com.heneria.nexus.command.NexusCommand;
import com.heneria.nexus.config.ConfigBundle;
import com.heneria.nexus.config.ConfigManager;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.scheduler.GamePhase;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.scheduler.RingScheduler.TaskProfile;
import com.heneria.nexus.service.ExecutorPools;
import com.heneria.nexus.service.ServiceRegistry;
import com.heneria.nexus.util.DumpUtil;
import com.heneria.nexus.util.MessageFacade;
import com.heneria.nexus.util.NexusLogger;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
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

    @Override
    public void onLoad() {
        this.logger = new NexusLogger(getLogger(), LOG_PREFIX);
        this.serviceRegistry = new ServiceRegistry(logger);
    }

    @Override
    public void onEnable() {
        this.logger = new NexusLogger(getLogger(), LOG_PREFIX);
        this.configManager = new ConfigManager(this, logger);
        ConfigManager.LoadResult loadResult = configManager.initialLoad();
        if (!loadResult.success()) {
            loadResult.errors().forEach(error -> logger.error("Erreur de configuration: " + error));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.bundle = loadResult.bundle();
        this.messageFacade = new MessageFacade(bundle.messages(), logger);
        this.executorPools = new ExecutorPools(logger, bundle.config().threadSettings());
        this.ringScheduler = new RingScheduler(this, logger);
        this.ringScheduler.applyPerfSettings(bundle.config().perfSettings());
        this.ringScheduler.registerProfile(TaskProfile.HUD,
                EnumSet.of(GamePhase.LOBBY, GamePhase.STARTING, GamePhase.PLAYING),
                () -> {
                });
        this.ringScheduler.registerProfile(TaskProfile.SCOREBOARD,
                EnumSet.of(GamePhase.STARTING, GamePhase.PLAYING, GamePhase.RESET),
                () -> {
                });
        this.dbProvider = new DbProvider(logger);

        serviceRegistry.register(RingScheduler.class, ringScheduler);
        serviceRegistry.register(DbProvider.class, dbProvider);

        registerCommands();
        logEnvironment();
        configureDatabase(bundle.config().databaseSettings());
    }

    @Override
    public void onDisable() {
        if (serviceRegistry != null) {
            serviceRegistry.shutdown().join();
        }
        if (executorPools != null) {
            executorPools.close();
        }
        logger.info("Nexus désactivé proprement");
    }

    private void configureDatabase(com.heneria.nexus.config.NexusConfig.DatabaseSettings settings) {
        ExecutorService ioExecutor = executorPools.ioExecutor();
        CompletableFuture<Boolean> future = dbProvider.applyConfiguration(settings, ioExecutor);
        future.thenAccept(success -> {
            if (!success) {
                logger.warn("Mode dégradé activé : MariaDB indisponible");
            }
        });
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
                bundle.config().perfSettings().hudHz(),
                bundle.config().perfSettings().scoreboardHz()));
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
        ringScheduler.applyPerfSettings(newBundle.config().perfSettings());
        messageFacade.update(newBundle.messages());
        this.bundle = newBundle;
        configManager.applyBundle(newBundle);
        configureDatabase(newBundle.config().databaseSettings());
    }

    public void handleDump(CommandSender sender) {
        if (!sender.hasPermission("nexus.admin.dump")) {
            messageFacade.send(sender, "commands.errors.no-permission");
            return;
        }
        messageFacade.send(sender, "commands.dump.header");
        List<Component> lines = DumpUtil.createDump(getServer(), bundle, executorPools, ringScheduler, dbProvider);
        lines.forEach(sender::sendMessage);
        messageFacade.send(sender, "commands.dump.success");
    }

    public MessageFacade messages() {
        return messageFacade;
    }

    public ConfigBundle bundle() {
        return bundle;
    }
}
