package com.heneria.nexusproxy.velocity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heneria.nexusproxy.velocity.command.NexusProxyCommand;
import com.heneria.nexusproxy.velocity.health.ServerAvailability;
import com.heneria.nexusproxy.velocity.health.ServerStatusRegistry;
import com.heneria.nexusproxy.velocity.health.ServerStatusSnapshot;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import javax.inject.Inject;
import org.slf4j.Logger;

@Plugin(id = "nexusproxy", name = "Nexus Proxy", version = "0.1.0-SNAPSHOT", authors = {"Heneria"})
public final class NexusProxyPlugin {

    private static final ChannelIdentifier HEALTH_CHANNEL =
            MinecraftChannelIdentifier.from("nexus:health");
    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(15);

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ServerStatusRegistry statusRegistry = new ServerStatusRegistry();

    private ScheduledTask cleanupTask;

    @Inject
    public NexusProxyPlugin(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxyServer.getChannelRegistrar().register(HEALTH_CHANNEL);
        registerCommands();
        cleanupTask = proxyServer.getScheduler().buildTask(this, () ->
                        statusRegistry.markExpired(STATUS_TIMEOUT, Instant.now()))
                .delay(STATUS_TIMEOUT)
                .repeat(STATUS_TIMEOUT.dividedBy(3))
                .schedule();
        logger.info("Canal nexus:health enregistré");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        proxyServer.getChannelRegistrar().unregister(HEALTH_CHANNEL);
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        logger.info("NexusProxy arrêté proprement");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(HEALTH_CHANNEL)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            logger.warn("PluginMessage nexus:health ignoré (source inconnue)");
            return;
        }
        try {
            HealthPayload payload = decode(event.getData());
            if (payload == null || payload.serverId() == null || payload.serverId().isBlank()) {
                logger.warn("PluginMessage nexus:health sans server_id depuis {}", connection.getServerInfo().getName());
                return;
            }
            String serverId = payload.serverId().trim();
            String connectionName = connection.getServerInfo().getName();
            if (!connectionName.equalsIgnoreCase(serverId)) {
                logger.warn("Incohérence server_id={} depuis {}", serverId, connectionName);
            }
            Instant now = Instant.now();
            ServerAvailability availability = parseAvailability(payload.status());
            ServerStatusSnapshot snapshot = new ServerStatusSnapshot(
                    serverId,
                    Math.max(0, payload.playerCount()),
                    Math.max(0, payload.maxPlayers()),
                    availability,
                    Math.max(0D, payload.tps()),
                    now);
            statusRegistry.update(snapshot);
        } catch (IOException exception) {
            logger.warn("PluginMessage nexus:health invalide reçu depuis {}", connectionName(event), exception);
        }
    }

    public ServerStatusRegistry statusRegistry() {
        return statusRegistry;
    }

    private HealthPayload decode(byte[] data) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            String json = input.readUTF();
            return objectMapper.readValue(json, HealthPayload.class);
        }
    }

    private void registerCommands() {
        CommandManager manager = proxyServer.getCommandManager();
        CommandMeta meta = manager.metaBuilder("nexusproxy")
                .plugin(this)
                .aliases("nxproxy")
                .build();
        manager.register(meta, new NexusProxyCommand(statusRegistry));
    }

    private ServerAvailability parseAvailability(String raw) {
        if (raw == null || raw.isBlank()) {
            return ServerAvailability.UNKNOWN;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return ServerAvailability.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            logger.warn("Statut d'arène inconnu reçu: {}", raw);
            return ServerAvailability.UNKNOWN;
        }
    }

    private String connectionName(PluginMessageEvent event) {
        if (event.getSource() instanceof ServerConnection connection) {
            return connection.getServerInfo().getName();
        }
        return "<source inconnue>";
    }

    private record HealthPayload(@JsonProperty("server_id") String serverId,
                                 @JsonProperty("player_count") int playerCount,
                                 @JsonProperty("max_players") int maxPlayers,
                                 @JsonProperty("status") String status,
                                 @JsonProperty("tps") double tps) {
    }
}
