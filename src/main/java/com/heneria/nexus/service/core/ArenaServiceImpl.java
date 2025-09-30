package com.heneria.nexus.service.core;

import com.heneria.nexus.analytics.AnalyticsService;
import com.heneria.nexus.analytics.MatchCompletedEvent;
import com.heneria.nexus.audit.AuditActionType;
import com.heneria.nexus.audit.AuditEntry;
import com.heneria.nexus.audit.AuditService;
import com.heneria.nexus.budget.BudgetService;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.api.ArenaBudget;
import com.heneria.nexus.api.ArenaCreationException;
import com.heneria.nexus.api.ArenaHandle;
import com.heneria.nexus.api.ArenaMode;
import com.heneria.nexus.api.ArenaPhase;
import com.heneria.nexus.api.ArenaService;
import com.heneria.nexus.api.EconomyService;
import com.heneria.nexus.api.MapDefinition;
import com.heneria.nexus.api.MapService;
import com.heneria.nexus.api.ProfileService;
import com.heneria.nexus.api.QueueService;
import com.heneria.nexus.api.TeleportService;
import com.heneria.nexus.api.events.NexusArenaEndEvent;
import com.heneria.nexus.api.events.NexusArenaStartEvent;
import com.heneria.nexus.api.map.MapBlueprint;
import com.heneria.nexus.api.map.MapBlueprint.MapTeam;
import com.heneria.nexus.api.map.MapBlueprint.MapVector;
import com.heneria.nexus.db.repository.MatchRepository;
import com.heneria.nexus.match.MatchSnapshot;
import com.heneria.nexus.scheduler.GamePhase;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.util.NexusLogger;
import com.heneria.nexus.watchdog.WatchdogService;
import com.heneria.nexus.watchdog.WatchdogTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Default arena orchestration service.
 */
public final class ArenaServiceImpl implements ArenaService {

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final MapService mapService;
    private final QueueService queueService;
    private final TeleportService teleportService;
    private final RingScheduler ringScheduler;
    private final Optional<ProfileService> profileService;
    private final EconomyService economyService;
    private final ExecutorManager executorManager;
    private final BudgetService budgetService;
    private final MatchRepository matchRepository;
    private final WatchdogService watchdogService;
    private final ConcurrentHashMap<UUID, ArenaHandle> arenas = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, BukkitTask> countdownTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Instant> completionTimes = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, FreezeState> frozenPlayers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ArenaListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<CoreConfig.ArenaSettings> settingsRef;
    private final AtomicReference<CoreConfig.TimeoutSettings.WatchdogSettings> watchdogSettingsRef;
    private final Optional<AnalyticsService> analyticsService;
    private final AuditService auditService;

    public ArenaServiceImpl(JavaPlugin plugin,
                            NexusLogger logger,
                            MapService mapService,
                            QueueService queueService,
                            TeleportService teleportService,
                            RingScheduler ringScheduler,
                            Optional<ProfileService> profileService,
                            EconomyService economyService,
                            ExecutorManager executorManager,
                            BudgetService budgetService,
                            MatchRepository matchRepository,
                            Optional<AnalyticsService> analyticsService,
                            WatchdogService watchdogService,
                            AuditService auditService,
                            CoreConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.mapService = Objects.requireNonNull(mapService, "mapService");
        this.queueService = Objects.requireNonNull(queueService, "queueService");
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.ringScheduler = Objects.requireNonNull(ringScheduler, "ringScheduler");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.budgetService = Objects.requireNonNull(budgetService, "budgetService");
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService");
        this.watchdogService = Objects.requireNonNull(watchdogService, "watchdogService");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.settingsRef = new AtomicReference<>(config.arenaSettings());
        this.watchdogSettingsRef = new AtomicReference<>(config.timeoutSettings().watchdog());
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public ArenaHandle createInstance(String mapId, ArenaMode mode, OptionalLong seed) throws ArenaCreationException {
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(mode, "mode");
        if (mapService.getMap(mapId).isEmpty()) {
            throw new ArenaCreationException("Map inconnue: " + mapId);
        }
        ArenaHandle handle = new ArenaHandle(UUID.randomUUID(), mapId, mode, ArenaPhase.LOBBY);
        arenas.put(handle.id(), handle);
        budgetService.registerArena(handle);
        logger.info("Nouvelle arène " + handle.id() + " sur map " + mapId + " mode " + mode);
        seed.ifPresent(value -> logger.debug(() -> "Seed déterministe appliqué à " + handle.id() + " -> " + value));
        return handle;
    }

    @Override
    public Optional<ArenaHandle> getInstance(UUID arenaId) {
        return Optional.ofNullable(arenas.get(arenaId));
    }

    @Override
    public Collection<ArenaHandle> instances() {
        return List.copyOf(arenas.values());
    }

    @Override
    public void transition(ArenaHandle handle, ArenaPhase nextPhase) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(nextPhase, "nextPhase");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Les transitions d'arène doivent être réalisées sur le main thread");
        }
        ArenaPhase previous = handle.setPhase(nextPhase);
        GamePhase schedulerPhase = GamePhase.valueOf(nextPhase.name());
        ringScheduler.setPhase(schedulerPhase);
        logger.debug(() -> "Transition d'arène " + handle.id() + " : " + previous + " -> " + nextPhase);

        switch (nextPhase) {
            case LOBBY -> enterLobby(handle);
            case STARTING -> beginStarting(handle);
            case PLAYING -> beginPlaying(handle);
            case SCORED -> enterScored(handle);
            case RESET -> beginReset(handle);
            case END -> prepareEnd(handle, previous);
            default -> { }
        }

        listeners.forEach(listener -> safe(() -> listener.onPhaseChange(handle, previous, nextPhase)));
        if (nextPhase == ArenaPhase.RESET) {
            listeners.forEach(listener -> safe(() -> listener.onResetStart(handle)));
            scheduleReset(handle);
            executorManager.compute().execute(() -> logger.debug(() -> "Préparation du reset pour " + handle.id()));
        } else if (previous == ArenaPhase.RESET && nextPhase != ArenaPhase.RESET) {
            listeners.forEach(listener -> safe(() -> listener.onResetEnd(handle)));
        }
        if (nextPhase == ArenaPhase.END) {
            finishArena(handle);
        }
    }

    private void teleportPlayersToHub(ArenaHandle handle) {
        List<Player> players = playersInArena(handle);
        if (players.isEmpty()) {
            logger.debug(() -> "Aucun joueur à rapatrier pour l'arène " + handle.id());
            return;
        }
        players.forEach(player -> {
            UUID playerId = player.getUniqueId();
            teleportService.returnToHub(playerId).whenComplete((result, throwable) ->
                    executorManager.mainThread().runNow(() -> handleHubTeleportResult(player, result, throwable)));
        });
    }

    private void handleHubTeleportResult(Player player,
                                         TeleportService.TeleportResult result,
                                         Throwable throwable) {
        if (!player.isOnline()) {
            return;
        }
        if (throwable != null) {
            logger.warn("Retour hub impossible pour " + player.getName(), throwable);
            return;
        }
        if (result == null || result.success()) {
            return;
        }
        String reason = result.message().isBlank() ? "Destination indisponible" : result.message();
        logger.warn("Retour hub refusé pour " + player.getName() + " : " + reason
                + " (" + result.status() + ")");
    }

    private void enterLobby(ArenaHandle handle) {
        cancelCountdown(handle);
        unfreezePlayers(handle);
        completionTimes.remove(handle.id());
        applyLobbyWorldRules(handle);
        setWorldPvP(handle, false);
    }

    private void beginStarting(ArenaHandle handle) {
        cancelCountdown(handle);
        setWorldPvP(handle, false);
        applyLobbyWorldRules(handle);
        teleportPlayersToSpawns(handle);
        distributeStartingKits(handle);
        startCountdown(handle);
    }

    private void beginPlaying(ArenaHandle handle) {
        cancelCountdown(handle);
        unfreezePlayers(handle);
        setWorldPvP(handle, true);
        plugin.getServer().getPluginManager().callEvent(new NexusArenaStartEvent(handle));
    }

    private void enterScored(ArenaHandle handle) {
        cancelCountdown(handle);
        setWorldPvP(handle, false);
        freezePlayers(handle);
        showScoreTitles(handle);
        Instant completedAt = Instant.now();
        completionTimes.put(handle.id(), completedAt);
        persistMatchSnapshot(handle, completedAt);
        if (economyService.isDegraded()) {
            logger.warn("Économie en mode dégradé lors du score pour " + handle.id());
        }
    }

    private void beginReset(ArenaHandle handle) {
        cancelCountdown(handle);
        unfreezePlayers(handle);
        setWorldPvP(handle, false);
        teleportPlayersToHub(handle);
    }

    private void prepareEnd(ArenaHandle handle, ArenaPhase previous) {
        cancelCountdown(handle);
        unfreezePlayers(handle);
        setWorldPvP(handle, false);
        if (previous != ArenaPhase.RESET) {
            teleportPlayersToHub(handle);
        }
    }

    private void finishArena(ArenaHandle handle) {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.callEvent(new NexusArenaEndEvent(handle, null));
        Instant completedAt = completionTimes.remove(handle.id());
        if (completedAt == null) {
            completedAt = Instant.now();
            persistMatchSnapshot(handle, completedAt);
        }
        Instant finalCompletedAt = completedAt;
        analyticsService.ifPresent(service -> service.record(new MatchCompletedEvent(
                handle.id(),
                handle.mapId(),
                handle.mode().name(),
                handle.createdAt(),
                finalCompletedAt)));
        budgetService.unregisterArena(handle);
        arenas.remove(handle.id());
        executorManager.compute().execute(() -> queueService.tryMatch(handle.mode()).ifPresent(plan ->
                logger.info("Match prêt après fin d'arène " + handle.id() + " -> " + plan.matchId())));
        profileService.ifPresent(service -> {
            if (service.isDegraded()) {
                logger.warn("Profil en mode dégradé détecté lors de la fermeture de " + handle.id());
            }
        });
    }

    private void applyLobbyWorldRules(ArenaHandle handle) {
        resolveArenaWorld(handle).ifPresentOrElse(world -> {
            world.setGameRule(GameRule.DO_TILE_DROPS, false);
            world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        }, () -> logger.debug(() -> "Impossible d'appliquer les règles de lobby pour l'arène " + handle.id()));
    }

    private void setWorldPvP(ArenaHandle handle, boolean enabled) {
        resolveArenaWorld(handle).ifPresent(world -> {
            if (world.getPVP() != enabled) {
                world.setPVP(enabled);
            }
        });
    }

    private void teleportPlayersToSpawns(ArenaHandle handle) {
        Optional<MapDefinition> definitionOptional = mapService.getMap(handle.mapId());
        if (definitionOptional.isEmpty()) {
            logger.warn("Impossible de téléporter les joueurs: map " + handle.mapId() + " inconnue");
            return;
        }
        Optional<World> worldOptional = resolveArenaWorld(handle);
        if (worldOptional.isEmpty()) {
            logger.debug(() -> "Monde introuvable pour téléporter les joueurs de l'arène " + handle.id());
            return;
        }
        World world = worldOptional.get();
        List<Player> players = List.copyOf(world.getPlayers());
        if (players.isEmpty()) {
            return;
        }
        MapBlueprint blueprint = definitionOptional.get().blueprint();
        if (blueprint == null || blueprint.teams().isEmpty()) {
            logger.warn("Aucun point de spawn défini pour l'arène " + handle.id());
            return;
        }
        List<MapVector> spawns = blueprint.teams().stream()
                .map(MapTeam::spawn)
                .filter(Objects::nonNull)
                .filter(MapVector::hasCoordinates)
                .toList();
        if (spawns.isEmpty()) {
            logger.warn("Points de spawn invalides pour l'arène " + handle.id());
            return;
        }
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            MapVector spawn = spawns.get(index % spawns.size());
            Location location = toLocation(world, spawn);
            if (location != null) {
                player.teleport(location);
            }
        }
    }

    private void distributeStartingKits(ArenaHandle handle) {
        List<Player> players = playersInArena(handle);
        if (players.isEmpty()) {
            return;
        }
        logger.debug(() -> "Distribution des kits de départ planifiée pour l'arène " + handle.id());
        Component actionBar = Component.text("Préparation de votre classe...", NamedTextColor.AQUA);
        players.forEach(player -> player.sendActionBar(actionBar));
    }

    private void startCountdown(ArenaHandle handle) {
        cancelCountdown(handle);
        AtomicInteger remainingSeconds = new AtomicInteger(15);
        BukkitTask task = executorManager.mainThread().runRepeating(() -> {
            int current = remainingSeconds.getAndDecrement();
            if (current < 0) {
                cancelCountdown(handle);
                return;
            }
            Title title = Title.title(
                    Component.text("La partie commence", NamedTextColor.GOLD),
                    Component.text(current > 0 ? current + "s" : "Combat !", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(1), Duration.ofMillis(250)));
            Component actionBar = current > 0
                    ? Component.text("Début dans " + current + "s", NamedTextColor.GOLD)
                    : Component.text("Bonne chance !", NamedTextColor.GREEN);
            playersInArena(handle).forEach(player -> {
                player.showTitle(title);
                player.sendActionBar(actionBar);
            });
            if (current <= 0) {
                cancelCountdown(handle);
            }
        }, 0L, 20L);
        if (task != null) {
            countdownTasks.put(handle.id(), task);
        }
    }

    private void cancelCountdown(ArenaHandle handle) {
        BukkitTask task = countdownTasks.remove(handle.id());
        if (task != null) {
            task.cancel();
        }
    }

    private void freezePlayers(ArenaHandle handle) {
        playersInArena(handle).forEach(player -> freezePlayer(handle, player));
    }

    private void freezePlayer(ArenaHandle handle, Player player) {
        UUID playerId = player.getUniqueId();
        frozenPlayers.computeIfAbsent(playerId, id -> {
            FreezeState state = new FreezeState(handle.id(),
                    player.getWalkSpeed(),
                    player.getFlySpeed(),
                    player.getAllowFlight(),
                    player.isFlying(),
                    player.getFreezeTicks());
            player.setSprinting(false);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setWalkSpeed(0F);
            player.setFlySpeed(0F);
            player.setFreezeTicks(Integer.MAX_VALUE / 4);
            return state;
        });
    }

    private void unfreezePlayers(ArenaHandle handle) {
        frozenPlayers.entrySet().removeIf(entry -> {
            FreezeState state = entry.getValue();
            if (!state.arenaId().equals(handle.id())) {
                return false;
            }
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                restorePlayerState(player, state);
            }
            return true;
        });
    }

    private void restorePlayerState(Player player, FreezeState state) {
        try {
            player.setWalkSpeed(state.walkSpeed());
        } catch (IllegalArgumentException ignored) {
            player.setWalkSpeed(0.2F);
        }
        try {
            player.setFlySpeed(state.flySpeed());
        } catch (IllegalArgumentException ignored) {
            player.setFlySpeed(0.1F);
        }
        player.setAllowFlight(state.allowFlight());
        if (state.allowFlight()) {
            player.setFlying(state.wasFlying());
        }
        player.setFreezeTicks(Math.max(0, state.freezeTicks()));
    }

    private void showScoreTitles(ArenaHandle handle) {
        Title title = Title.title(
                Component.text("Partie terminée", NamedTextColor.GOLD),
                Component.text("Bravo à tous !", NamedTextColor.GREEN),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500)));
        Component actionBar = Component.text("Sauvegarde des résultats...", NamedTextColor.YELLOW);
        playersInArena(handle).forEach(player -> {
            player.showTitle(title);
            player.sendActionBar(actionBar);
        });
    }

    private List<Player> playersInArena(ArenaHandle handle) {
        return resolveArenaWorld(handle)
                .map(world -> List.copyOf(world.getPlayers()))
                .orElse(List.of());
    }

    private Optional<World> resolveArenaWorld(ArenaHandle handle) {
        World world = Bukkit.getWorld(handle.id().toString());
        if (world != null) {
            return Optional.of(world);
        }
        world = Bukkit.getWorld(handle.mapId());
        if (world != null) {
            return Optional.of(world);
        }
        return Optional.empty();
    }

    private Location toLocation(World world, MapVector vector) {
        if (vector == null || !vector.hasCoordinates()) {
            return null;
        }
        double x = vector.x();
        double y = vector.y();
        double z = vector.z();
        float yaw = vector.yaw() != null ? vector.yaw() : 0F;
        float pitch = vector.pitch() != null ? vector.pitch() : 0F;
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void safe(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            logger.error("Listener d'arène en échec", throwable);
        }
    }

    @Override
    public ArenaBudget budget(ArenaHandle handle) {
        CoreConfig.ArenaSettings settings = settingsRef.get();
        return new ArenaBudget(settings.maxEntities(), settings.maxItems(), settings.maxProjectiles());
    }

    @Override
    public void registerListener(ArenaListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void unregisterListener(ArenaListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void applyArenaSettings(CoreConfig.ArenaSettings settings) {
        settingsRef.set(Objects.requireNonNull(settings, "settings"));
        ringScheduler.applyPerfSettings(settings);
        logger.info("Paramètres d'arène mis à jour: hud=" + settings.hudHz() + " scoreboard=" + settings.scoreboardHz());
    }

    @Override
    public void applyWatchdogSettings(CoreConfig.TimeoutSettings.WatchdogSettings settings) {
        watchdogSettingsRef.set(Objects.requireNonNull(settings, "settings"));
        logger.info("Timeouts watchdog mis à jour: reset=" + settings.resetMs() + "ms paste=" + settings.pasteMs() + "ms");
    }

    private void persistMatchSnapshot(ArenaHandle handle, Instant completedAt) {
        MatchSnapshot snapshot = buildSnapshot(handle, completedAt);
        matchRepository.save(snapshot).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                logger.error("Impossible de persister le match " + handle.id(), throwable);
            } else {
                logger.debug(() -> "Snapshot du match " + handle.id() + " sauvegardé");
            }
        });
    }

    private MatchSnapshot buildSnapshot(ArenaHandle handle, Instant completedAt) {
        return new MatchSnapshot(
                handle.id(),
                handle.mapId(),
                handle.mode(),
                handle.createdAt(),
                completedAt,
                Optional.empty(),
                List.of());
    }

    private void scheduleReset(ArenaHandle handle) {
        CoreConfig.TimeoutSettings.WatchdogSettings settings = watchdogSettingsRef.get();
        Duration timeout = Duration.ofMillis(Math.max(1L, settings.resetMs()));
        String taskName = "arena-reset-" + handle.id();
        watchdogService.registerFallback(taskName, throwable -> forceEnd(handle, throwable));
        logArenaReset(handle, "reset-start", null);
        watchdogService.monitor(taskName, timeout, () -> performReset(handle)).whenComplete((unused, throwable) -> {
            if (throwable == null) {
                logArenaReset(handle, "reset-complete", null);
                executorManager.mainThread().runLater(() -> transition(handle, ArenaPhase.END), 1L);
                return;
            }
            if (throwable instanceof WatchdogTimeoutException timeoutException) {
                logArenaReset(handle, "reset-timeout", timeoutException);
                return;
            }
            logger.warn("Reset de l'arène " + handle.id() + " terminé avec une erreur", throwable);
            logArenaReset(handle, "reset-error", throwable);
            forceEnd(handle, throwable);
        });
    }

    private CompletableFuture<Void> performReset(ArenaHandle handle) {
        return executorManager.runCompute(() ->
                logger.debug(() -> "Réinitialisation de l'arène " + handle.id() + " sur map " + handle.mapId()))
                .thenCompose(ignored -> monitorPaste(handle));
    }

    private CompletableFuture<Void> monitorPaste(ArenaHandle handle) {
        CoreConfig.TimeoutSettings.WatchdogSettings settings = watchdogSettingsRef.get();
        Duration timeout = Duration.ofMillis(Math.max(1L, settings.pasteMs()));
        String taskName = "arena-paste-" + handle.id();
        watchdogService.registerFallback(taskName, throwable -> forceEnd(handle, throwable));
        return watchdogService.monitor(taskName, timeout, () -> executorManager.runCompute(() ->
                logger.debug(() -> "Application du schematic pour " + handle.id())));
    }

    private void forceEnd(ArenaHandle handle, Throwable cause) {
        executorManager.mainThread().runNow(() -> {
            if (!arenas.containsKey(handle.id())) {
                return;
            }
            if (cause != null) {
                logger.warn("Arène " + handle.id() + " forcée en phase END suite à un incident de reset.", cause);
                logArenaReset(handle, "reset-force-end", cause);
            } else {
                logger.warn("Arène " + handle.id() + " forcée en phase END suite à un incident de reset.");
                logArenaReset(handle, "reset-force-end", null);
            }
            transition(handle, ArenaPhase.END);
        });
    }

    private void logArenaReset(ArenaHandle handle, String event, Throwable cause) {
        StringBuilder details = new StringBuilder();
        details.append("event=").append(event)
                .append("; arena=").append(handle.id())
                .append("; map=").append(handle.mapId())
                .append("; mode=").append(handle.mode().name());
        if (cause != null) {
            String message = cause.getMessage();
            details.append("; reason=")
                    .append(message != null && !message.isBlank() ? message : cause.getClass().getSimpleName());
        }
        auditService.log(new AuditEntry(
                null,
                null,
                AuditActionType.SYSTEM_EVENT,
                handle.id(),
                null,
                details.toString()));
    }

    private record FreezeState(UUID arenaId,
                               float walkSpeed,
                               float flySpeed,
                               boolean allowFlight,
                               boolean wasFlying,
                               int freezeTicks) {
    }
}
