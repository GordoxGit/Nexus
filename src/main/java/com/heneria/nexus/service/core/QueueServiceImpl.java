package com.heneria.nexus.service.core;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.api.ArenaMode;
import com.heneria.nexus.api.MatchPlan;
import com.heneria.nexus.api.QueueOptions;
import com.heneria.nexus.api.QueueService;
import com.heneria.nexus.api.QueueSnapshot;
import com.heneria.nexus.api.QueueStats;
import com.heneria.nexus.api.QueueTicket;
import com.heneria.nexus.api.ProfileService;
import com.heneria.nexus.api.TeleportService;
import com.heneria.nexus.redis.RedisService;
import com.heneria.nexus.util.NexusLogger;
import com.heneria.nexus.util.MessageFacade;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Default matchmaking service using in-memory queues.
 */
public final class QueueServiceImpl implements QueueService {

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final ExecutorManager executorManager;
    private final ProfileService profileService;
    private final TeleportService teleportService;
    private final MessageFacade messageFacade;
    private final RedisService redisService;
    private final Optional<LuckPerms> luckPerms;
    private final GlobalMatchmaker globalMatchmaker;
    private final Map<ArenaMode, ConcurrentLinkedQueue<QueueTicket>> queues = new EnumMap<>(ArenaMode.class);
    private final ConcurrentHashMap<UUID, QueueTicket> ticketsByPlayer = new ConcurrentHashMap<>();
    private final AtomicLong matchesFormed = new AtomicLong();
    private final AtomicReference<CoreConfig.QueueSettings> settingsRef;
    private final AtomicReference<QueueStats> stats = new AtomicReference<>(new QueueStats(0, 0, 0L, 0L));
    private final AtomicBoolean crossShardFallbackLogged = new AtomicBoolean();
    private final String serverId;
    private volatile BukkitTask tickerTask;

    public QueueServiceImpl(JavaPlugin plugin,
                            NexusLogger logger,
                            ExecutorManager executorManager,
                            ProfileService profileService,
                            TeleportService teleportService,
                            MessageFacade messageFacade,
                            RedisService redisService,
                            HealthCheckService healthCheckService,
                            CoreConfig config,
                            Optional<LuckPerms> luckPerms) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.messageFacade = Objects.requireNonNull(messageFacade, "messageFacade");
        this.redisService = Objects.requireNonNull(redisService, "redisService");
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        Objects.requireNonNull(healthCheckService, "healthCheckService");
        Objects.requireNonNull(config, "config");
        this.settingsRef = new AtomicReference<>(config.queueSettings());
        this.serverId = Objects.requireNonNull(config.serverId(), "serverId");
        this.globalMatchmaker = new GlobalMatchmaker(logger,
                executorManager,
                this.redisService,
                healthCheckService,
                settingsRef::get,
                this::findLocalTicket,
                this::isPlayerOnlineLocally,
                this.serverId);
        for (ArenaMode mode : ArenaMode.values()) {
            queues.put(mode, new ConcurrentLinkedQueue<>());
        }
    }

    @Override
    public CompletableFuture<Void> start() {
        return executorManager.runCompute(this::scheduleTicker);
    }

    private void scheduleTicker() {
        cancelTicker();
        long period = Math.max(1L, Math.round(20.0d / Math.max(1, settingsRef.get().tickHz())));
        tickerTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
                executorManager.compute().execute(this::matchAllModes), period, period);
        logger.info("QueueService démarré (période=" + period + " ticks)");
    }

    private void cancelTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private void matchAllModes() {
        for (ArenaMode mode : ArenaMode.values()) {
            tryMatch(mode).ifPresent(plan -> matchesFormed.incrementAndGet());
        }
        updateStats();
    }

    @Override
    public CompletableFuture<Void> stop() {
        return executorManager.runCompute(this::cancelTicker);
    }

    @Override
    public QueueTicket enqueue(UUID playerId, ArenaMode mode, QueueOptions options) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mode, "mode");
        QueueOptions effectiveOptions = options == null ? QueueOptions.standard() : options;
        QueueTicket ticket = new QueueTicket(playerId, mode, effectiveOptions, Instant.now());
        ticketsByPlayer.put(playerId, ticket);
        int weight = weightForTicket(ticket);
        if (shouldUseCrossShard()) {
            enqueueCrossShard(ticket, weight);
        } else {
            queues.get(mode).add(ticket);
        }
        profileService.load(playerId).exceptionally(throwable -> {
            logger.debug(() -> "Préchargement du profil impossible pour " + playerId + ": " + throwable.getMessage());
            return null;
        });
        updateStats();
        return ticket;
    }

    @Override
    public void leave(UUID playerId) {
        QueueTicket ticket = ticketsByPlayer.remove(playerId);
        if (ticket != null) {
            ConcurrentLinkedQueue<QueueTicket> queue = queues.get(ticket.mode());
            if (queue != null) {
                queue.remove(ticket);
            }
            removeFromRedis(ticket);
            updateStats();
        }
    }

    @Override
    public QueueSnapshot snapshot() {
        Collection<QueueTicket> tickets = ticketsByPlayer.values();
        return new QueueSnapshot(tickets.stream().sorted(Comparator.comparing(QueueTicket::enqueuedAt)).toList());
    }

    @Override
    public QueueStats stats() {
        return stats.get();
    }

    @Override
    public Optional<MatchPlan> tryMatch(ArenaMode mode) {
        Objects.requireNonNull(mode, "mode");
        int playersNeeded = playersNeededForMode(mode);
        if (playersNeeded <= 0) {
            return Optional.empty();
        }
        if (shouldUseCrossShard()) {
            Optional<GlobalMatchmaker.MatchResult> result = globalMatchmaker.tryMatch(mode, playersNeeded);
            result.ifPresent(this::handleGlobalMatch);
            return result.map(GlobalMatchmaker.MatchResult::plan);
        }
        ConcurrentLinkedQueue<QueueTicket> queue = queues.get(mode);
        if (queue == null) {
            return Optional.empty();
        }
        List<QueueTicket> candidates = new ArrayList<>(queue);
        if (candidates.size() < playersNeeded) {
            return Optional.empty();
        }
        candidates.sort(Comparator.comparingInt(this::weightForTicket).reversed()
                .thenComparing(QueueTicket::enqueuedAt));
        List<QueueTicket> selected = candidates.stream().limit(playersNeeded).toList();
        List<UUID> players = selected.stream().map(QueueTicket::playerId).toList();
        if (players.stream().anyMatch(id -> !ticketsByPlayer.containsKey(id))) {
            selected.forEach(queue::remove);
            return Optional.empty();
        }
        selected.forEach(ticket -> {
            ticketsByPlayer.remove(ticket.playerId());
            queue.remove(ticket);
        });
        updateStats();
        MatchPlan plan = new MatchPlan(UUID.randomUUID(), mode, players, Optional.empty());
        dispatchTeleportRequests(selected, Optional.empty());
        return Optional.of(plan);
    }

    @Override
    public void applySettings(CoreConfig.QueueSettings settings) {
        Objects.requireNonNull(settings, "settings");
        settingsRef.set(settings);
        scheduleTicker();
    }

    private void handleGlobalMatch(GlobalMatchmaker.MatchResult result) {
        if (result == null) {
            return;
        }
        List<QueueTicket> localTickets = new ArrayList<>();
        for (UUID playerId : result.plan().players()) {
            QueueTicket ticket = ticketsByPlayer.remove(playerId);
            if (ticket != null) {
                ConcurrentLinkedQueue<QueueTicket> queue = queues.get(ticket.mode());
                if (queue != null) {
                    queue.remove(ticket);
                }
                localTickets.add(ticket);
            }
        }
        if (!localTickets.isEmpty()) {
            dispatchTeleportRequests(localTickets, result.targetServerId());
        }
        updateStats();
    }

    private void dispatchTeleportRequests(List<QueueTicket> tickets, Optional<String> targetServerId) {
        if (tickets.isEmpty()) {
            return;
        }
        String target = targetServerId.filter(id -> !id.isBlank()).orElse(null);
        for (QueueTicket ticket : tickets) {
            UUID playerId = ticket.playerId();
            notifyTeleportStart(playerId);
            teleportService.connectToArena(playerId, target).whenComplete((result, throwable) ->
                    executorManager.mainThread().runNow(() -> handleTeleportOutcome(ticket, result, throwable)));
        }
    }

    private void enqueueCrossShard(QueueTicket ticket, int weight) {
        if (!redisService.isOperational()) {
            fallbackToLocalQueue(ticket);
            return;
        }
        String key = queueKeyFor(ticket.mode());
        double score = computeScore(ticket, weight);
        redisService.execute(jedis -> {
            jedis.zadd(key, score, ticket.playerId().toString());
            return null;
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                logger.warn("Impossible d'ajouter le joueur " + ticket.playerId() + " à la file Redis", throwable);
                if (ticketsByPlayer.containsKey(ticket.playerId())) {
                    fallbackToLocalQueue(ticket);
                }
            }
        });
    }

    private void removeFromRedis(QueueTicket ticket) {
        CoreConfig.QueueSettings settings = settingsRef.get();
        CoreConfig.QueueSettings.CrossShardSettings crossShard = settings.crossShard();
        if (!crossShard.enabled() || !redisService.isOperational()) {
            return;
        }
        String key = queueKeyFor(ticket.mode());
        redisService.execute(jedis -> {
            jedis.zrem(key, ticket.playerId().toString());
            return null;
        }).exceptionally(throwable -> {
            logger.debug(() -> "Suppression Redis ignorée pour " + ticket.playerId() + ": " + throwable.getMessage());
            return null;
        });
    }

    private void fallbackToLocalQueue(QueueTicket ticket) {
        if (ticket == null) {
            return;
        }
        ConcurrentLinkedQueue<QueueTicket> queue = queues.get(ticket.mode());
        if (queue != null) {
            queue.add(ticket);
        }
        updateStats();
    }

    private double computeScore(QueueTicket ticket, int weight) {
        int effectiveWeight = Math.max(0, Math.min(Integer.MAX_VALUE, weight));
        long priorityComponent = (long) Integer.MAX_VALUE - effectiveWeight;
        long timestamp = Math.max(0L, ticket.enqueuedAt().toEpochMilli());
        String formatted = String.format(Locale.ROOT, "%d.%013d", priorityComponent, timestamp);
        return Double.parseDouble(formatted);
    }

    private String queueKeyFor(ArenaMode mode) {
        CoreConfig.QueueSettings.CrossShardSettings crossShard = settingsRef.get().crossShard();
        return crossShard.redisKeyPrefix() + ":" + mode.name().toLowerCase(Locale.ROOT);
    }

    private boolean shouldUseCrossShard() {
        CoreConfig.QueueSettings settings = settingsRef.get();
        CoreConfig.QueueSettings.CrossShardSettings crossShard = settings.crossShard();
        if (crossShard == null || !crossShard.enabled()) {
            crossShardFallbackLogged.set(false);
            return false;
        }
        RedisService.ConnectionState redisState = redisService.state();
        if (redisState == RedisService.ConnectionState.DEGRADED) {
            if (crossShardFallbackLogged.compareAndSet(false, true)) {
                logger.warn("Redis en mode dégradé — file d'attente locale activée.");
            }
            return false;
        }
        if (!redisService.isOperational()) {
            if (crossShardFallbackLogged.compareAndSet(false, true)) {
                logger.warn("Redis indisponible — bascule vers la file locale pour le matchmaking.");
            }
            return false;
        }
        if (crossShardFallbackLogged.getAndSet(false)) {
            logger.info("Redis à nouveau disponible — reprise du matchmaking cross-shard.");
        }
        return true;
    }

    private int playersNeededForMode(ArenaMode mode) {
        return switch (mode) {
            case CASUAL, COMPETITIVE -> 2;
        };
    }

    private Optional<QueueTicket> findLocalTicket(UUID playerId) {
        return Optional.ofNullable(ticketsByPlayer.get(playerId));
    }

    private boolean isPlayerOnlineLocally(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        return player != null && player.isOnline();
    }

    private void notifyTeleportStart(UUID playerId) {
        executorManager.mainThread().runNow(() -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                messageFacade.send(player, "network.arena.teleporting");
            }
        });
    }

    private void handleTeleportOutcome(QueueTicket ticket,
                                       TeleportService.TeleportResult result,
                                       Throwable throwable) {
        UUID playerId = ticket.playerId();
        if (throwable != null) {
            logger.warn("Téléportation vers l'arène impossible pour " + playerId, throwable);
            notifyTeleportFailure(playerId, "Erreur interne");
            requeueIfOnline(ticket);
            return;
        }
        if (result == null) {
            logger.warn("Réponse de téléportation nulle pour " + playerId);
            notifyTeleportFailure(playerId, "Réponse invalide");
            requeueIfOnline(ticket);
            return;
        }
        if (result.success()) {
            return;
        }
        String reason = result.message().isBlank() ? "Destination indisponible" : result.message();
        logger.warn("Téléportation refusée pour " + playerId + " : " + reason
                + " (" + result.status() + ")");
        notifyTeleportFailure(playerId, reason);
        requeueIfOnline(ticket);
    }

    private void notifyTeleportFailure(UUID playerId, String reason) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            messageFacade.send(player, "network.arena.failed",
                    Placeholder.unparsed("reason", reason));
        }
    }

    private void requeueIfOnline(QueueTicket ticket) {
        Player player = plugin.getServer().getPlayer(ticket.playerId());
        if (player == null) {
            return;
        }
        executorManager.compute().execute(() -> enqueue(ticket.playerId(), ticket.mode(), ticket.options()));
    }

    private int weightForTicket(QueueTicket ticket) {
        int groupWeight = luckPerms
                .map(api -> highestGroupWeight(api, ticket.playerId()))
                .orElseGet(() -> ticket.options().vip() ? settingsRef.get().vipWeight() : 0);
        return ticket.options().weight() + groupWeight;
    }

    private void updateStats() {
        Collection<QueueTicket> tickets = ticketsByPlayer.values();
        long vip = tickets.stream().filter(ticket -> ticket.options().vip()).count();
        long totalWait = tickets.stream()
                .mapToLong(ticket -> Duration.between(ticket.enqueuedAt(), Instant.now()).getSeconds())
                .sum();
        long averageWait = tickets.isEmpty() ? 0L : totalWait / tickets.size();
        stats.set(new QueueStats(tickets.size(), (int) vip, averageWait, matchesFormed.get()));
    }

    private int highestGroupWeight(LuckPerms api, UUID playerId) {
        User user = api.getUserManager().getUser(playerId);
        if (user == null) {
            return 0;
        }
        GroupManager groupManager = api.getGroupManager();
        int best = weightOfGroup(groupManager, user.getPrimaryGroup());
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            best = Math.max(best, weightOfGroup(groupManager, node.getGroupName()));
        }
        return best;
    }

    private int weightOfGroup(GroupManager groupManager, String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return 0;
        }
        Group group = groupManager.getGroup(groupName);
        if (group == null) {
            return 0;
        }
        return group.getWeight().orElse(0);
    }
}
