package com.heneria.nexus.service.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heneria.nexus.api.ArenaHandle;
import com.heneria.nexus.api.ArenaPhase;
import com.heneria.nexus.api.ArenaService;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.scheduler.GamePhase;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.NexusLogger;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Periodically publishes lightweight health information to the Velocity proxy
 * using plugin messages.
 */
public final class HealthCheckService implements LifecycleAware {

    public static final String CHANNEL = "nexus:health";
    private static final String TASK_ID = "health-check";
    private static final double DEFAULT_TPS = 20D;

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final RingScheduler scheduler;
    private final Optional<ArenaService> arenaService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<Configuration> configurationRef;
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean channelRegistered = new AtomicBoolean();

    public HealthCheckService(JavaPlugin plugin,
                              NexusLogger logger,
                              RingScheduler scheduler,
                              Optional<ArenaService> arenaService,
                              CoreConfig coreConfig) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.arenaService = Objects.requireNonNull(arenaService, "arenaService");
        Objects.requireNonNull(coreConfig, "coreConfig");
        this.configurationRef = new AtomicReference<>(toConfiguration(coreConfig));
    }

    @Override
    public CompletableFuture<Void> start() {
        running.set(true);
        registerChannel();
        reschedule(configurationRef.get());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        running.set(false);
        scheduler.unregisterTask(TASK_ID);
        unregisterChannel();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isHealthy() {
        if (!configurationRef.get().enabled()) {
            return LifecycleAware.super.isHealthy();
        }
        return channelRegistered.get() && lastError.get() == null && LifecycleAware.super.isHealthy();
    }

    @Override
    public Optional<Throwable> lastError() {
        return Optional.ofNullable(lastError.get());
    }

    /**
     * Applies a freshly reloaded configuration.
     */
    public void applyConfiguration(CoreConfig coreConfig) {
        Objects.requireNonNull(coreConfig, "coreConfig");
        Configuration newConfiguration = toConfiguration(coreConfig);
        configurationRef.set(newConfiguration);
        if (!running.get()) {
            return;
        }
        reschedule(newConfiguration);
    }

    private void registerChannel() {
        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            channelRegistered.set(true);
        } catch (Throwable throwable) {
            channelRegistered.set(false);
            lastError.set(throwable);
            logger.warn("Impossible d'enregistrer le canal de health check", throwable);
        }
    }

    private void unregisterChannel() {
        try {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Throwable throwable) {
            logger.warn("Erreur lors de la désinscription du canal de health check", throwable);
        } finally {
            channelRegistered.set(false);
        }
    }

    private void reschedule(Configuration configuration) {
        scheduler.unregisterTask(TASK_ID);
        if (!configuration.enabled()) {
            logger.debug(() -> "HealthCheck désactivé — aucun ping ne sera envoyé");
            return;
        }
        scheduler.registerTask(TASK_ID, configuration.intervalTicks(), EnumSet.allOf(GamePhase.class), this::publishSnapshot);
    }

    private void publishSnapshot() {
        Configuration configuration = configurationRef.get();
        if (!configuration.enabled() || !plugin.isEnabled()) {
            return;
        }
        if (!channelRegistered.get()) {
            registerChannel();
            if (!channelRegistered.get()) {
                return;
            }
        }
        ServerSnapshot snapshot = new ServerSnapshot(
                configuration.serverId(),
                plugin.getServer().getOnlinePlayers().size(),
                plugin.getServer().getMaxPlayers(),
                resolveAvailability().name(),
                roundTps(queryCurrentTps()));
        byte[] payload;
        try {
            payload = encode(snapshot);
        } catch (IOException exception) {
            lastError.set(exception);
            logger.warn("Impossible de sérialiser le health check", exception);
            return;
        }
        try {
            plugin.getServer().sendPluginMessage(plugin, CHANNEL, payload);
            lastError.set(null);
        } catch (Throwable throwable) {
            lastError.set(throwable);
            logger.warn("Impossible d'envoyer le PluginMessage de health check", throwable);
        }
    }

    private byte[] encode(ServerSnapshot snapshot) throws IOException {
        String json;
        try {
            json = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw exception;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(json.length() + 4);
        try (DataOutputStream data = new DataOutputStream(output)) {
            data.writeUTF(json);
        }
        return output.toByteArray();
    }

    private ServerAvailability resolveAvailability() {
        return arenaService.map(this::resolveFromArenas).orElse(ServerAvailability.UNKNOWN);
    }

    private ServerAvailability resolveFromArenas(ArenaService service) {
        Collection<ArenaHandle> arenas = service.instances();
        if (arenas.isEmpty()) {
            return ServerAvailability.LOBBY;
        }
        boolean hasStarting = false;
        boolean hasPlaying = false;
        boolean hasEnding = false;
        for (ArenaHandle handle : arenas) {
            ArenaPhase phase = handle.phase();
            if (phase == null) {
                continue;
            }
            switch (phase) {
                case STARTING -> hasStarting = true;
                case PLAYING, SCORED -> hasPlaying = true;
                case RESET, END -> hasEnding = true;
                default -> {
                    // noop
                }
            }
        }
        if (hasPlaying) {
            return ServerAvailability.IN_GAME;
        }
        if (hasEnding) {
            return ServerAvailability.ENDING;
        }
        if (hasStarting) {
            return ServerAvailability.STARTING;
        }
        return ServerAvailability.LOBBY;
    }

    private double queryCurrentTps() {
        try {
            double[] tpsValues = plugin.getServer().getTPS();
            if (tpsValues.length == 0) {
                return DEFAULT_TPS;
            }
            double value = tpsValues[0];
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return DEFAULT_TPS;
            }
            return value;
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
            return DEFAULT_TPS;
        }
    }

    private double roundTps(double tps) {
        return Math.round(tps * 100D) / 100D;
    }

    private Configuration toConfiguration(CoreConfig coreConfig) {
        CoreConfig.HealthCheckSettings settings = coreConfig.healthCheckSettings();
        long intervalTicks = Math.max(1L, settings.intervalSeconds() * 20L);
        return new Configuration(settings.enabled(), coreConfig.serverId(), intervalTicks);
    }

    private record Configuration(boolean enabled, String serverId, long intervalTicks) {
    }

    private record ServerSnapshot(String serverId, int playerCount, int maxPlayers, String status, double tps) {
    }

    private enum ServerAvailability {
        LOBBY,
        STARTING,
        IN_GAME,
        ENDING,
        UNKNOWN
    }
}
