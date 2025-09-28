package com.heneria.nexus.service.core;

import com.heneria.nexus.api.ArenaMode;
import com.heneria.nexus.api.MatchPlan;
import com.heneria.nexus.api.QueueTicket;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.redis.RedisService;
import com.heneria.nexus.util.NexusLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import redis.clients.jedis.params.SetParams;

/**
 * Performs matchmaking using Redis when the queue operates in cross-shard mode.
 */
public final class GlobalMatchmaker {

    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final NexusLogger logger;
    private final RedisService redisService;
    private final HealthCheckService healthCheckService;
    private final Supplier<CoreConfig.QueueSettings> settingsSupplier;
    private final Function<UUID, Optional<QueueTicket>> localTicketSupplier;
    private final Predicate<UUID> localOnlinePredicate;
    private final String serverId;

    public GlobalMatchmaker(NexusLogger logger,
                            ExecutorManager executorManager,
                            RedisService redisService,
                            HealthCheckService healthCheckService,
                            Supplier<CoreConfig.QueueSettings> settingsSupplier,
                            Function<UUID, Optional<QueueTicket>> localTicketSupplier,
                            Predicate<UUID> localOnlinePredicate,
                            String serverId) {
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(executorManager, "executorManager");
        this.redisService = Objects.requireNonNull(redisService, "redisService");
        this.healthCheckService = Objects.requireNonNull(healthCheckService, "healthCheckService");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.localTicketSupplier = Objects.requireNonNull(localTicketSupplier, "localTicketSupplier");
        this.localOnlinePredicate = Objects.requireNonNull(localOnlinePredicate, "localOnlinePredicate");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
    }

    public Optional<MatchResult> tryMatch(ArenaMode mode, int playersNeeded) {
        Objects.requireNonNull(mode, "mode");
        if (playersNeeded <= 0) {
            return Optional.empty();
        }
        CoreConfig.QueueSettings settings = settingsSupplier.get();
        CoreConfig.QueueSettings.CrossShardSettings crossShard = settings.crossShard();
        if (crossShard == null || !crossShard.enabled() || !redisService.isOperational()) {
            return Optional.empty();
        }
        String lockKey = crossShard.redisKeyPrefix() + ":lock";
        String token = serverId + ":" + UUID.randomUUID();
        boolean locked = false;
        try {
            locked = acquireLock(lockKey, token, crossShard.lockTtlMs());
            if (!locked) {
                return Optional.empty();
            }
            logger.info(() -> "GlobalMatchmaker: verrou acquis par " + serverId + " pour " + mode);
            return attemptMatch(mode, playersNeeded, settings);
        } catch (Exception exception) {
            logger.warn("Erreur lors du matchmaking global pour " + mode, exception);
            return Optional.empty();
        } finally {
            if (locked) {
                releaseLock(lockKey, token);
            }
        }
    }

    private Optional<MatchResult> attemptMatch(ArenaMode mode, int playersNeeded, CoreConfig.QueueSettings settings) {
        String queueKey = queueKey(settings.crossShard().redisKeyPrefix(), mode);
        List<String> rawMembers = fetchTopMembers(queueKey, playersNeeded);
        if (rawMembers.isEmpty() || rawMembers.size() < playersNeeded) {
            return Optional.empty();
        }
        List<String> invalidMembers = new ArrayList<>();
        List<String> validMembers = new ArrayList<>();
        List<UUID> validPlayers = new ArrayList<>();
        for (String member : rawMembers) {
            if (member == null || member.isBlank()) {
                continue;
            }
            UUID playerId;
            try {
                playerId = UUID.fromString(member);
            } catch (IllegalArgumentException exception) {
                logger.debug(() -> "Identifiant invalide détecté dans la file Redis: " + member);
                invalidMembers.add(member);
                continue;
            }
            Optional<QueueTicket> localTicket = localTicketSupplier.apply(playerId);
            if (localTicket.isPresent() && !localOnlinePredicate.test(playerId)) {
                logger.debug(() -> "Joueur local hors-ligne détecté, retrait de la file: " + playerId);
                invalidMembers.add(member);
                continue;
            }
            validMembers.add(member);
            validPlayers.add(playerId);
        }
        if (!invalidMembers.isEmpty()) {
            removeMembers(queueKey, invalidMembers);
        }
        if (validPlayers.size() < playersNeeded) {
            return Optional.empty();
        }
        List<String> matchedMembers = new ArrayList<>(validMembers.subList(0, playersNeeded));
        List<UUID> matchedPlayers = new ArrayList<>(validPlayers.subList(0, playersNeeded));
        removeMembers(queueKey, matchedMembers);
        Optional<String> targetServer = resolveTargetServer(settings);
        MatchPlan plan = new MatchPlan(UUID.randomUUID(), mode, matchedPlayers, Optional.empty());
        return Optional.of(new MatchResult(plan, targetServer));
    }

    private boolean acquireLock(String lockKey, String token, long ttlMs) {
        try {
            return Boolean.TRUE.equals(redisService.execute(jedis -> {
                SetParams params = SetParams.setParams().nx().px(Math.max(1L, ttlMs));
                String response = jedis.set(lockKey, token, params);
                return response != null && response.equalsIgnoreCase("OK");
            }).join());
        } catch (CompletionException exception) {
            logger.warn("Impossible d'acquérir le verrou Redis pour le matchmaking", exception.getCause());
            return false;
        }
    }

    private void releaseLock(String lockKey, String token) {
        try {
            redisService.execute(jedis -> {
                jedis.eval(RELEASE_LOCK_SCRIPT, List.of(lockKey), List.of(token));
                return null;
            }).join();
        } catch (CompletionException exception) {
            logger.debug(() -> "Impossible de libérer le verrou Redis: " + exception.getCause().getMessage());
        }
    }

    private List<String> fetchTopMembers(String queueKey, int playersNeeded) {
        try {
            return redisService.execute(jedis -> jedis.zrange(queueKey, 0, playersNeeded - 1)).join();
        } catch (CompletionException exception) {
            logger.warn("Lecture de la file Redis impossible", exception.getCause());
            return List.of();
        }
    }

    private void removeMembers(String queueKey, List<String> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        try {
            redisService.execute(jedis -> {
                jedis.zrem(queueKey, members.toArray(String[]::new));
                return null;
            }).join();
        } catch (CompletionException exception) {
            logger.debug(() -> "Suppression partielle de la file impossible: " + exception.getCause().getMessage());
        }
    }

    private Optional<String> resolveTargetServer(CoreConfig.QueueSettings settings) {
        Optional<String> fromHealth = healthCheckService.findAvailableServerId()
                .filter(id -> !id.isBlank());
        if (fromHealth.isPresent()) {
            return fromHealth;
        }
        String fallback = settings.targetServerId();
        if (fallback != null && !fallback.isBlank()) {
            return Optional.of(fallback);
        }
        return Optional.empty();
    }

    private String queueKey(String prefix, ArenaMode mode) {
        return prefix + ":" + mode.name().toLowerCase(Locale.ROOT);
    }

    public record MatchResult(MatchPlan plan, Optional<String> targetServerId) {
    }
}
