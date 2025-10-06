package com.heneria.nexus.service.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heneria.nexus.api.TeleportService;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.security.ChannelSecurityManager;
import com.heneria.nexus.security.NetworkRateLimiter;
import com.heneria.nexus.service.core.payload.TeleportResultPayload;
import com.heneria.nexus.util.NexusLogger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Default implementation of {@link TeleportService} relying on Velocity plugin
 * messages.
 */
public final class TeleportServiceImpl implements TeleportService, PluginMessageListener {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10L);
    private static final String SUBCHANNEL_CONNECT = "Connect";
    private static final String SUBCHANNEL_RETURN = "ReturnToHub";
    private static final String SUBCHANNEL_RESULT = "Result";
    private static final String PROXY_SOURCE = "velocity-proxy";

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final ExecutorManager executorManager;
    private final ChannelSecurityManager channelSecurityManager;
    private final NetworkRateLimiter networkRateLimiter;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CoreConfig.QueueSettings> settingsRef;
    private final ConcurrentHashMap<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final AtomicBoolean channelRegistered = new AtomicBoolean();

    public TeleportServiceImpl(JavaPlugin plugin,
                               NexusLogger logger,
                               ExecutorManager executorManager,
                               ChannelSecurityManager channelSecurityManager,
                               NetworkRateLimiter networkRateLimiter,
                               CoreConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.channelSecurityManager = Objects.requireNonNull(channelSecurityManager, "channelSecurityManager");
        this.networkRateLimiter = Objects.requireNonNull(networkRateLimiter, "networkRateLimiter");
        Objects.requireNonNull(config, "config");
        this.settingsRef = new AtomicReference<>(config.queueSettings());
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @Override
    public CompletableFuture<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executorManager.mainThread().runNow(() -> {
            try {
                if (!plugin.isEnabled()) {
                    IllegalStateException exception = new IllegalStateException("Plugin désactivé");
                    lastError.set(exception);
                    future.completeExceptionally(exception);
                    return;
                }
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
                plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
                channelRegistered.set(true);
                lastError.set(null);
                future.complete(null);
            } catch (Throwable throwable) {
                lastError.set(throwable);
                channelRegistered.set(false);
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> stop() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executorManager.mainThread().runNow(() -> {
            try {
                plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
                plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
            } catch (Throwable throwable) {
                logger.warn("Erreur lors de la désinscription du canal plugin message", throwable);
            } finally {
                channelRegistered.set(false);
                pending.values().forEach(PendingTeleport::cancelTimeout);
                pending.values().forEach(pendingTeleport -> pendingTeleport.future.complete(new TeleportResult(
                        pendingTeleport.requestId,
                        pendingTeleport.playerId,
                        pendingTeleport.action,
                        TeleportStatus.TIMEOUT,
                        "Canal fermé")));
                pending.clear();
                future.complete(null);
            }
        });
        return future;
    }

    @Override
    public boolean isHealthy() {
        return channelRegistered.get() && TeleportService.super.isHealthy();
    }

    @Override
    public Optional<Throwable> lastError() {
        return Optional.ofNullable(lastError.get());
    }

    @Override
    public CompletableFuture<TeleportResult> connectToArena(UUID playerId, String serverId) {
        Objects.requireNonNull(playerId, "playerId");
        String target = serverId;
        CoreConfig.QueueSettings current = settingsRef.get();
        if (target == null || target.isBlank()) {
            target = current.targetServerId();
        }
        if (target == null || target.isBlank()) {
            UUID requestId = UUID.randomUUID();
            TeleportResult result = new TeleportResult(requestId, playerId, TeleportAction.CONNECT,
                    TeleportStatus.FAILED, "Aucun serveur Nexus configuré");
            return CompletableFuture.completedFuture(result);
        }
        return dispatchRequest(playerId, TeleportAction.CONNECT, Map.of("server", target));
    }

    @Override
    public CompletableFuture<TeleportResult> returnToHub(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CoreConfig.QueueSettings current = settingsRef.get();
        String hubGroup = current.hubGroup();
        if (hubGroup == null || hubGroup.isBlank()) {
            UUID requestId = UUID.randomUUID();
            TeleportResult result = new TeleportResult(requestId, playerId, TeleportAction.RETURN_TO_HUB,
                    TeleportStatus.FAILED, "Aucun groupe hub configuré");
            return CompletableFuture.completedFuture(result);
        }
        return dispatchRequest(playerId, TeleportAction.RETURN_TO_HUB, Map.of("group", hubGroup));
    }

    @Override
    public void applySettings(CoreConfig.QueueSettings settings) {
        settingsRef.set(Objects.requireNonNull(settings, "settings"));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channelSecurityManager.isChannelAllowed(channel)) {
            String origin = player != null ? player.getName() + "/" + player.getUniqueId() : "<inconnu>";
            logger.warn(String.format(
                    "PluginMessage reçu sur un canal non autorisé: %s (origine: %s)", channel, origin));
            return;
        }
        String sourceServerId = resolveSourceServerId(player);
        networkRateLimiter.isAllowed(sourceServerId, channel).whenComplete((allowed, throwable) -> {
            if (throwable != null) {
                logger.warn(String.format("Erreur lors du contrôle du débit réseau sur %s", channel), throwable);
                return;
            }
            if (!Boolean.TRUE.equals(allowed)) {
                return;
            }
            handlePluginMessage(channel, message);
        });
    }

    private void handlePluginMessage(String channel, byte[] message) {
        if (!CHANNEL.equalsIgnoreCase(channel)) {
            return;
        }
        if (message == null || message.length == 0) {
            return;
        }
        Optional<TeleportResultPayload> payload = decodeTeleportPayload(message);
        if (payload.isEmpty()) {
            return;
        }
        TeleportResultPayload data = payload.get();
        if (data.requestId() == null) {
            logger.warn("PluginMessage reçu sans identifiant de requête sur {}", CHANNEL);
            return;
        }
        TeleportStatus status = parseStatus(data.status());
        completePending(data.requestId(), status, data.message());
    }

    private String resolveSourceServerId(Player player) {
        return PROXY_SOURCE;
    }

    private Optional<TeleportResultPayload> decodeTeleportPayload(byte[] message) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            String token = input.readUTF();
            if (token == null || token.isBlank()) {
                logger.warn("PluginMessage reçu sans contenu sur {}", CHANNEL);
                return Optional.empty();
            }
            if (token.trim().startsWith("{")) {
                TeleportResultPayload payload = objectMapper.readValue(token, TeleportResultPayload.class);
                return Optional.of(payload);
            }
            if (!SUBCHANNEL_RESULT.equalsIgnoreCase(token)) {
                logger.debug(() -> "Sous-canal plugin message inattendu: " + token);
                return Optional.empty();
            }
            UUID requestId = UUID.fromString(input.readUTF());
            String statusRaw = safeReadUtf(input);
            String reason = safeReadUtf(input);
            return Optional.of(new TeleportResultPayload(requestId, statusRaw, reason));
        } catch (JsonProcessingException exception) {
            logger.warn("PluginMessage JSON invalide reçu sur " + CHANNEL, exception);
        } catch (Exception exception) {
            logger.warn("PluginMessage invalide reçu sur " + CHANNEL, exception);
        }
        return Optional.empty();
    }

    private CompletableFuture<TeleportResult> dispatchRequest(UUID playerId,
                                                               TeleportAction action,
                                                               Map<String, String> arguments) {
        CompletableFuture<TeleportResult> future = new CompletableFuture<>();
        if (!channelRegistered.get()) {
            UUID requestId = UUID.randomUUID();
            TeleportResult result = new TeleportResult(requestId, playerId, action, TeleportStatus.FAILED,
                    "Canal plugin message inactif");
            future.complete(result);
            return future;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            UUID requestId = UUID.randomUUID();
            TeleportResult result = new TeleportResult(requestId, playerId, action, TeleportStatus.FAILED,
                    "Joueur déconnecté");
            future.complete(result);
            return future;
        }
        UUID requestId = UUID.randomUUID();
        byte[] payload;
        try {
            payload = encodePayload(requestId, playerId, action, arguments);
        } catch (IOException exception) {
            lastError.set(exception);
            TeleportResult result = new TeleportResult(requestId, playerId, action, TeleportStatus.FAILED,
                    "Encodage du message impossible");
            future.complete(result);
            return future;
        }
        PendingTeleport pendingTeleport = registerPending(requestId, player.getUniqueId(), action, future);
        executorManager.mainThread().runNow(() -> {
            if (!plugin.isEnabled() || !player.isOnline()) {
                failPending(requestId, player.getUniqueId(), action, TeleportStatus.FAILED,
                        "Joueur déconnecté", future);
                return;
            }
            try {
                player.sendPluginMessage(plugin, CHANNEL, payload);
            } catch (Throwable throwable) {
                lastError.set(throwable);
                logger.warn("Impossible d'envoyer un PluginMessage de téléportation", throwable);
                failPending(requestId, player.getUniqueId(), action, TeleportStatus.FAILED,
                        "Téléportation impossible", future);
            }
        });
        future.whenComplete((ignored, throwable) -> pendingTeleport.cancelTimeout());
        return future;
    }

    private PendingTeleport registerPending(UUID requestId,
                                            UUID playerId,
                                            TeleportAction action,
                                            CompletableFuture<TeleportResult> future) {
        long timeoutTicks = Math.max(20L, REQUEST_TIMEOUT.toMillis() / 50L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> handleTimeout(requestId), timeoutTicks);
        PendingTeleport pendingTeleport = new PendingTeleport(requestId, playerId, action, future, task);
        pending.put(requestId, pendingTeleport);
        return pendingTeleport;
    }

    private void handleTimeout(UUID requestId) {
        PendingTeleport pendingTeleport = pending.remove(requestId);
        if (pendingTeleport == null) {
            return;
        }
        TeleportResult result = new TeleportResult(requestId, pendingTeleport.playerId, pendingTeleport.action,
                TeleportStatus.TIMEOUT, "Aucune réponse du proxy");
        pendingTeleport.future.complete(result);
    }

    private void completePending(UUID requestId, TeleportStatus status, String reason) {
        PendingTeleport pendingTeleport = pending.remove(requestId);
        if (pendingTeleport == null) {
            logger.debug(() -> "Réponse de téléportation sans requête associée: " + requestId);
            return;
        }
        pendingTeleport.cancelTimeout();
        TeleportResult result = new TeleportResult(requestId, pendingTeleport.playerId, pendingTeleport.action, status,
                reason);
        pendingTeleport.future.complete(result);
    }

    private void failPending(UUID requestId,
                             UUID playerId,
                             TeleportAction action,
                             TeleportStatus status,
                             String reason,
                             CompletableFuture<TeleportResult> future) {
        PendingTeleport pendingTeleport = pending.remove(requestId);
        if (pendingTeleport != null) {
            pendingTeleport.cancelTimeout();
        }
        TeleportResult result = new TeleportResult(requestId, playerId, action, status, reason);
        future.complete(result);
    }

    private TeleportStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return TeleportStatus.FAILED;
        }
        String normalized = raw.trim().toUpperCase(StandardCharsets.US_ASCII);
        return switch (normalized) {
            case "SUCCESS", "OK" -> TeleportStatus.SUCCESS;
            case "RETRY", "RETRYABLE", "BUSY" -> TeleportStatus.RETRYABLE;
            case "TIMEOUT" -> TeleportStatus.TIMEOUT;
            default -> TeleportStatus.FAILED;
        };
    }

    private byte[] encodePayload(UUID requestId,
                                 UUID playerId,
                                 TeleportAction action,
                                 Map<String, String> arguments) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(outputStream)) {
            if (action == TeleportAction.CONNECT) {
                output.writeUTF(SUBCHANNEL_CONNECT);
            } else {
                output.writeUTF(SUBCHANNEL_RETURN);
            }
            output.writeUTF(requestId.toString());
            output.writeUTF(playerId.toString());
            if (action == TeleportAction.CONNECT) {
                output.writeUTF(arguments.getOrDefault("server", ""));
            } else {
                output.writeUTF(arguments.getOrDefault("group", ""));
            }
        }
        return outputStream.toByteArray();
    }

    private String safeReadUtf(DataInputStream input) throws IOException {
        if (input.available() <= 0) {
            return "";
        }
        return input.readUTF();
    }

    private static final class PendingTeleport {
        private final UUID requestId;
        private final UUID playerId;
        private final TeleportAction action;
        private final CompletableFuture<TeleportResult> future;
        private final BukkitTask timeoutTask;

        private PendingTeleport(UUID requestId,
                                UUID playerId,
                                TeleportAction action,
                                CompletableFuture<TeleportResult> future,
                                BukkitTask timeoutTask) {
            this.requestId = requestId;
            this.playerId = playerId;
            this.action = action;
            this.future = future;
            this.timeoutTask = timeoutTask;
        }

        private void cancelTimeout() {
            if (timeoutTask != null) {
                try {
                    timeoutTask.cancel();
                } catch (Exception ignored) {
                    // Ignore cancellation failures when the task already executed.
                }
            }
        }
    }
}
