package com.heneria.nexus.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.util.NexusLogger;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * High level facade exposed to other services when Redis is enabled.
 */
public final class RedisManager {

    private static final String CHANNEL_PARTY_INVITE = "nexus:party:invite";
    private static final String CHANNEL_ANNOUNCEMENT = "nexus:announcement";

    private final NexusLogger logger;
    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisManager(NexusLogger logger, RedisService redisService, CoreConfig coreConfig) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.redisService = Objects.requireNonNull(redisService, "redisService");
        Objects.requireNonNull(coreConfig, "coreConfig");
        redisService.applySettings(coreConfig.redisSettings());
    }

    public void applySettings(CoreConfig.RedisSettings settings) {
        Objects.requireNonNull(settings, "settings");
        redisService.applySettings(settings);
    }

    public boolean isEnabled() {
        return redisService.isEnabled();
    }

    public boolean isOperational() {
        return redisService.isOperational();
    }

    public RedisService.RedisDiagnostics diagnostics() {
        return redisService.diagnostics();
    }

    public CompletableFuture<Void> sendPartyInvite(UUID inviterUuid,
                                                   String inviterName,
                                                   UUID targetUuid,
                                                   String targetName) {
        if (!redisService.isEnabled()) {
            logger.debug(() -> "Invitation party ignorée (Redis désactivé).");
            return CompletableFuture.completedFuture(null);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "party_invite");
        Optional.ofNullable(inviterUuid).ifPresent(uuid -> payload.put("inviter_uuid", uuid.toString()));
        Optional.ofNullable(inviterName).filter(name -> !name.isBlank()).ifPresent(name -> payload.put("inviter_name", name));
        Optional.ofNullable(targetUuid).ifPresent(uuid -> payload.put("target_uuid", uuid.toString()));
        Optional.ofNullable(targetName).filter(name -> !name.isBlank()).ifPresent(name -> payload.put("target_name", name));
        payload.put("timestamp", System.currentTimeMillis());
        return publishJson(CHANNEL_PARTY_INVITE, payload);
    }

    public CompletableFuture<Void> broadcastAnnouncement(String message) {
        if (!redisService.isEnabled()) {
            logger.debug(() -> "Annonce globale ignorée (Redis désactivé).");
            return CompletableFuture.completedFuture(null);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "announcement");
        payload.put("message", Objects.requireNonNullElse(message, ""));
        payload.put("timestamp", System.currentTimeMillis());
        return publishJson(CHANNEL_ANNOUNCEMENT, payload);
    }

    public RedisService.RedisSubscription subscribe(String channel, RedisMessageListener listener) {
        return redisService.subscribe(channel, listener);
    }

    private CompletableFuture<Void> publishJson(String channel, ObjectNode payload) {
        if (!redisService.isOperational()) {
            logger.debug(() -> "Publication Redis ignorée (hors ligne) sur " + channel);
            return CompletableFuture.completedFuture(null);
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return redisService.publish(channel, json).thenApply(ignored -> null);
    }
}
