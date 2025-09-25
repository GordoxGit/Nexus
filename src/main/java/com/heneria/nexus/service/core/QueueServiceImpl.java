package com.heneria.nexus.service.core;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.service.api.ArenaMode;
import com.heneria.nexus.service.api.MatchPlan;
import com.heneria.nexus.service.api.QueueOptions;
import com.heneria.nexus.service.api.QueueService;
import com.heneria.nexus.service.api.QueueSnapshot;
import com.heneria.nexus.service.api.QueueStats;
import com.heneria.nexus.service.api.QueueTicket;
import com.heneria.nexus.service.api.ProfileService;
import com.heneria.nexus.util.NexusLogger;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
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
    private final Optional<LuckPerms> luckPerms;
    private final Map<ArenaMode, ConcurrentLinkedQueue<QueueTicket>> queues = new EnumMap<>(ArenaMode.class);
    private final ConcurrentHashMap<UUID, QueueTicket> ticketsByPlayer = new ConcurrentHashMap<>();
    private final AtomicLong matchesFormed = new AtomicLong();
    private final AtomicReference<CoreConfig.QueueSettings> settingsRef;
    private final AtomicReference<QueueStats> stats = new AtomicReference<>(new QueueStats(0, 0, 0L, 0L));
    private volatile BukkitTask tickerTask;

    public QueueServiceImpl(JavaPlugin plugin,
                            NexusLogger logger,
                            ExecutorManager executorManager,
                            ProfileService profileService,
                            CoreConfig config,
                            Optional<LuckPerms> luckPerms) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        this.settingsRef = new AtomicReference<>(config.queueSettings());
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
        queues.get(mode).add(ticket);
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
            queues.get(ticket.mode()).remove(ticket);
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
        ConcurrentLinkedQueue<QueueTicket> queue = queues.get(mode);
        if (queue == null) {
            return Optional.empty();
        }
        List<QueueTicket> candidates = new ArrayList<>(queue);
        if (candidates.size() < 2) {
            return Optional.empty();
        }
        candidates.sort(Comparator.comparingInt(this::weightForTicket).reversed()
                .thenComparing(QueueTicket::enqueuedAt));
        List<QueueTicket> selected = candidates.stream().limit(2).toList();
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
        return Optional.of(plan);
    }

    @Override
    public void applySettings(CoreConfig.QueueSettings settings) {
        Objects.requireNonNull(settings, "settings");
        settingsRef.set(settings);
        scheduleTicker();
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
