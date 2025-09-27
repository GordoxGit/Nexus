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
import com.heneria.nexus.api.MapService;
import com.heneria.nexus.api.ProfileService;
import com.heneria.nexus.api.QueueService;
import com.heneria.nexus.api.events.NexusArenaEndEvent;
import com.heneria.nexus.api.events.NexusArenaStartEvent;
import com.heneria.nexus.db.repository.MatchRepository;
import com.heneria.nexus.util.NexusLogger;
import com.heneria.nexus.watchdog.WatchdogService;
import com.heneria.nexus.watchdog.WatchdogTimeoutException;
import com.heneria.nexus.match.MatchSnapshot;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Default arena orchestration service.
 */
public final class ArenaServiceImpl implements ArenaService {

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final MapService mapService;
    private final QueueService queueService;
    private final Optional<ProfileService> profileService;
    private final EconomyService economyService;
    private final ExecutorManager executorManager;
    private final BudgetService budgetService;
    private final MatchRepository matchRepository;
    private final WatchdogService watchdogService;
    private final ConcurrentHashMap<UUID, ArenaHandle> arenas = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ArenaListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<CoreConfig.ArenaSettings> settingsRef;
    private final AtomicReference<CoreConfig.TimeoutSettings.WatchdogSettings> watchdogSettingsRef;
    private final Optional<AnalyticsService> analyticsService;
    private final AuditService auditService;

    public ArenaServiceImpl(JavaPlugin plugin,
                            NexusLogger logger,
                            MapService mapService,
                            QueueService queueService,
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
        if (!plugin.getServer().isPrimaryThread()) {
            throw new IllegalStateException("Les transitions d'arène doivent être réalisées sur le main thread");
        }
        ArenaPhase previous = handle.setPhase(nextPhase);
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        if (nextPhase == ArenaPhase.PLAYING) {
            pluginManager.callEvent(new NexusArenaStartEvent(handle));
        }
        listeners.forEach(listener -> safe(() -> listener.onPhaseChange(handle, previous, nextPhase)));
        if (nextPhase == ArenaPhase.RESET) {
            listeners.forEach(listener -> safe(() -> listener.onResetStart(handle)));
            scheduleReset(handle);
            executorManager.compute().execute(() -> logger.debug(() -> "Préparation du reset pour " + handle.id()));
        } else if (previous == ArenaPhase.RESET && nextPhase != ArenaPhase.RESET) {
            listeners.forEach(listener -> safe(() -> listener.onResetEnd(handle)));
        } else if (nextPhase == ArenaPhase.SCORED) {
            if (economyService.isDegraded()) {
                logger.warn("Économie en mode dégradé lors du score pour " + handle.id());
            }
        } else if (nextPhase == ArenaPhase.END) {
            pluginManager.callEvent(new NexusArenaEndEvent(handle, null));
            Instant completedAt = Instant.now();
            analyticsService.ifPresent(service -> service.record(new MatchCompletedEvent(
                    handle.id(),
                    handle.mapId(),
                    handle.mode().name(),
                    handle.createdAt(),
                    completedAt)));
            persistMatchSnapshot(handle, completedAt);
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
}
