package com.heneria.nexus.budget;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.api.ArenaHandle;
import com.heneria.nexus.service.api.ArenaMode;
import com.heneria.nexus.util.NexusLogger;
import io.papermc.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Default implementation of {@link BudgetService} enforcing per arena budgets.
 */
public final class BudgetServiceImpl implements BudgetService {

    private static final Pattern WORLD_ID_PATTERN = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private static final long WARN_INTERVAL_MS = TimeUnit.SECONDS.toMillis(10);

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final NamespacedKey arenaKey;
    private final ConcurrentHashMap<UUID, BudgetTracker> trackers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, EntityRegistration> trackedEntities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> worldArenaIndex = new ConcurrentHashMap<>();
    private final AtomicReference<CoreConfig.ArenaSettings> settingsRef;
    private final CopyOnWriteArrayList<Listener> registeredListeners = new CopyOnWriteArrayList<>();

    public BudgetServiceImpl(JavaPlugin plugin, NexusLogger logger, CoreConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(config, "config");
        this.settingsRef = new AtomicReference<>(config.arenaSettings());
        this.arenaKey = new NamespacedKey(plugin, "arena-id");
    }

    @Override
    public CompletableFuture<Void> start() {
        if (!registeredListeners.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        PluginManager manager = plugin.getServer().getPluginManager();
        Listener entitySpawn = new com.heneria.nexus.budget.listener.EntitySpawnListener(this);
        Listener itemSpawn = new com.heneria.nexus.budget.listener.ItemSpawnListener(this);
        Listener projectileSpawn = new com.heneria.nexus.budget.listener.ProjectileLaunchListener(this);
        Listener lifecycle = new EntityLifecycleListener();
        manager.registerEvents(entitySpawn, plugin);
        manager.registerEvents(itemSpawn, plugin);
        manager.registerEvents(projectileSpawn, plugin);
        manager.registerEvents(lifecycle, plugin);
        registeredListeners.addAll(List.of(entitySpawn, itemSpawn, projectileSpawn, lifecycle));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        registeredListeners.forEach(HandlerList::unregisterAll);
        registeredListeners.clear();
        trackers.clear();
        trackedEntities.clear();
        worldArenaIndex.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void registerArena(ArenaHandle handle) {
        Objects.requireNonNull(handle, "handle");
        CoreConfig.ArenaSettings settings = settingsRef.get();
        trackers.put(handle.id(), new BudgetTracker(handle, settings));
    }

    @Override
    public void unregisterArena(ArenaHandle handle) {
        Objects.requireNonNull(handle, "handle");
        trackers.remove(handle.id());
        trackedEntities.entrySet().removeIf(entry -> entry.getValue().arenaId.equals(handle.id()));
        worldArenaIndex.entrySet().removeIf(entry -> entry.getValue().equals(handle.id()));
    }

    @Override
    public boolean canSpawn(UUID arenaId, EntityType type, int amount) {
        Objects.requireNonNull(arenaId, "arenaId");
        Objects.requireNonNull(type, "type");
        if (amount <= 0) {
            return true;
        }
        BudgetTracker tracker = trackers.get(arenaId);
        if (tracker == null) {
            return true;
        }
        BudgetType budgetType = classify(type);
        if (budgetType == null || budgetType == BudgetType.PARTICLE) {
            return true;
        }
        if (tracker.canReserve(budgetType, amount)) {
            return true;
        }
        tracker.warnExceeded(budgetType, logger);
        return false;
    }

    @Override
    public void markPending(UUID arenaId, Entity entity, BudgetType type) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(type, "type");
        BudgetTracker tracker = trackers.get(arenaId);
        if (tracker == null) {
            return;
        }
        tracker.addPending(type, 1);
        trackedEntities.compute(entity.getUniqueId(), (id, existing) ->
                new EntityRegistration(arenaId, type, true));
    }

    @Override
    public void cancelPending(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        trackedEntities.computeIfPresent(entity.getUniqueId(), (id, registration) -> {
            if (!registration.pending) {
                return registration;
            }
            BudgetTracker tracker = trackers.get(registration.arenaId);
            if (tracker != null) {
                tracker.cancelPending(registration.type, 1);
            }
            return null;
        });
    }

    @Override
    public void recordEntityAdded(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        BudgetType type = classify(entity);
        if (type == null) {
            return;
        }
        UUID entityId = entity.getUniqueId();
        trackedEntities.compute(entityId, (id, registration) -> {
            if (registration != null) {
                BudgetTracker tracker = trackers.get(registration.arenaId);
                if (tracker == null) {
                    return null;
                }
                if (registration.pending) {
                    tracker.confirm(registration.type, 1);
                    registration.pending = false;
                }
                worldArenaIndex.put(entity.getWorld().getUID(), registration.arenaId);
                return registration;
            }
            Optional<UUID> arenaId = resolveArenaId(entity.getLocation());
            if (arenaId.isEmpty()) {
                return null;
            }
            BudgetTracker tracker = trackers.get(arenaId.get());
            if (tracker == null) {
                return null;
            }
            tracker.addExisting(type, 1);
            worldArenaIndex.put(entity.getWorld().getUID(), arenaId.get());
            return new EntityRegistration(arenaId.get(), type, false);
        });
    }

    @Override
    public void recordEntityRemoved(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        trackedEntities.computeIfPresent(entity.getUniqueId(), (id, registration) -> {
            BudgetTracker tracker = trackers.get(registration.arenaId);
            if (tracker != null) {
                if (registration.pending) {
                    tracker.cancelPending(registration.type, 1);
                } else {
                    tracker.release(registration.type, 1);
                }
            }
            return null;
        });
    }

    @Override
    public void trackParticle(UUID arenaId, int count) {
        if (count <= 0) {
            return;
        }
        BudgetTracker tracker = trackers.get(arenaId);
        if (tracker == null) {
            return;
        }
        tracker.trackParticles(count, logger);
    }

    @Override
    public Optional<BudgetSnapshot> getSnapshot(UUID arenaId) {
        BudgetTracker tracker = trackers.get(arenaId);
        if (tracker == null) {
            return Optional.empty();
        }
        return Optional.of(tracker.snapshot());
    }

    @Override
    public Collection<BudgetSnapshot> snapshots() {
        List<BudgetSnapshot> snapshots = new ArrayList<>();
        trackers.values().forEach(tracker -> snapshots.add(tracker.snapshot()));
        return List.copyOf(snapshots);
    }

    @Override
    public Optional<UUID> resolveArenaId(Location location) {
        if (location == null) {
            return Optional.empty();
        }
        World world = location.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        UUID worldId = world.getUID();
        UUID cached = worldArenaIndex.get(worldId);
        if (cached != null && trackers.containsKey(cached)) {
            return Optional.of(cached);
        }
        PersistentDataContainer container = world.getPersistentDataContainer();
        String stored = container.get(arenaKey, PersistentDataType.STRING);
        if (stored != null) {
            try {
                UUID parsed = UUID.fromString(stored);
                if (trackers.containsKey(parsed)) {
                    worldArenaIndex.put(worldId, parsed);
                    return Optional.of(parsed);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        Matcher matcher = WORLD_ID_PATTERN.matcher(world.getName());
        if (matcher.find()) {
            try {
                UUID parsed = UUID.fromString(matcher.group(1));
                if (trackers.containsKey(parsed)) {
                    worldArenaIndex.put(worldId, parsed);
                    return Optional.of(parsed);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    @Override
    public void applySettings(CoreConfig.ArenaSettings settings) {
        Objects.requireNonNull(settings, "settings");
        settingsRef.set(settings);
        trackers.values().forEach(tracker -> tracker.apply(settings));
    }

    private BudgetType classify(EntityType type) {
        if (type == EntityType.PLAYER) {
            return null;
        }
        if (type == EntityType.DROPPED_ITEM) {
            return BudgetType.ITEM;
        }
        Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass != null && Projectile.class.isAssignableFrom(entityClass)) {
            return BudgetType.PROJECTILE;
        }
        return BudgetType.ENTITY;
    }

    private BudgetType classify(Entity entity) {
        if (entity instanceof Player) {
            return null;
        }
        if (entity.getType() == EntityType.DROPPED_ITEM) {
            return BudgetType.ITEM;
        }
        if (entity instanceof Projectile) {
            return BudgetType.PROJECTILE;
        }
        return BudgetType.ENTITY;
    }

    private final class EntityLifecycleListener implements Listener {

        @EventHandler
        public void onEntityAdd(EntityAddToWorldEvent event) {
            recordEntityAdded(event.getEntity());
        }

        @EventHandler
        public void onEntityRemove(EntityRemoveFromWorldEvent event) {
            recordEntityRemoved(event.getEntity());
        }
    }

    private static final class EntityRegistration {
        private final UUID arenaId;
        private final BudgetType type;
        private volatile boolean pending;

        private EntityRegistration(UUID arenaId, BudgetType type, boolean pending) {
            this.arenaId = arenaId;
            this.type = type;
            this.pending = pending;
        }
    }

    private static final class BudgetTracker {

        private final UUID arenaId;
        private final String mapId;
        private final ArenaMode mode;
        private final Map<BudgetType, Counter> active;
        private final Map<BudgetType, Counter> pending;
        private final Map<BudgetType, AtomicLong> warnTimestamps;
        private volatile int maxEntities;
        private volatile int maxItems;
        private volatile int maxProjectiles;
        private volatile int particlesSoftCap;
        private volatile int particlesHardCap;
        private final Object particleLock = new Object();
        private final AtomicLong particleWindow = new AtomicLong(-1L);
        private final Counter particleCounter = new Counter();

        private BudgetTracker(ArenaHandle handle, CoreConfig.ArenaSettings settings) {
            this.arenaId = handle.id();
            this.mapId = handle.mapId();
            this.mode = handle.mode();
            this.active = Map.of(
                    BudgetType.ENTITY, new Counter(),
                    BudgetType.ITEM, new Counter(),
                    BudgetType.PROJECTILE, new Counter(),
                    BudgetType.PARTICLE, new Counter());
            this.pending = Map.of(
                    BudgetType.ENTITY, new Counter(),
                    BudgetType.ITEM, new Counter(),
                    BudgetType.PROJECTILE, new Counter(),
                    BudgetType.PARTICLE, new Counter());
            this.warnTimestamps = Map.of(
                    BudgetType.ENTITY, new AtomicLong(),
                    BudgetType.ITEM, new AtomicLong(),
                    BudgetType.PROJECTILE, new AtomicLong(),
                    BudgetType.PARTICLE, new AtomicLong());
            apply(settings);
        }

        private void apply(CoreConfig.ArenaSettings settings) {
            this.maxEntities = settings.maxEntities();
            this.maxItems = settings.maxItems();
            this.maxProjectiles = settings.maxProjectiles();
            this.particlesSoftCap = settings.particlesSoftCap();
            this.particlesHardCap = settings.particlesHardCap();
        }

        private boolean canReserve(BudgetType type, int amount) {
            long limit = limitFor(type);
            if (limit <= 0) {
                return false;
            }
            long used = active.get(type).value() + pending.get(type).value();
            return used + amount <= limit;
        }

        private void addPending(BudgetType type, int amount) {
            pending.get(type).add(amount);
        }

        private void confirm(BudgetType type, int amount) {
            pending.get(type).remove(amount);
            active.get(type).add(amount);
        }

        private void cancelPending(BudgetType type, int amount) {
            pending.get(type).remove(amount);
        }

        private void addExisting(BudgetType type, int amount) {
            active.get(type).add(amount);
        }

        private void release(BudgetType type, int amount) {
            active.get(type).remove(amount);
        }

        private void trackParticles(int count, NexusLogger logger) {
            if (count <= 0) {
                return;
            }
            long current;
            synchronized (particleLock) {
                long window = System.currentTimeMillis() / 50L;
                long previous = particleWindow.get();
                if (previous != window) {
                    particleCounter.reset();
                    particleWindow.set(window);
                }
                particleCounter.add(count);
                current = particleCounter.value();
            }
            if (current > particlesHardCap) {
                warnParticles(logger, current, true);
            } else if (current > particlesSoftCap) {
                warnParticles(logger, current, false);
            }
        }

        private void warnExceeded(BudgetType type, NexusLogger logger) {
            long now = System.currentTimeMillis();
            if (!shouldWarn(type, now)) {
                return;
            }
            long activeCount = active.get(type).value();
            long pendingCount = pending.get(type).value();
            long limit = limitFor(type);
            String message = "Budget pour %s dépassé dans l'arène %s (map=%s, mode=%s). Spawn annulé. (Actuel: %d, en file: %d, Max: %d)"
                    .formatted(type.label(), arenaId, mapId, mode, activeCount, pendingCount, limit);
            logger.warn(message);
        }

        private void warnParticles(NexusLogger logger, long current, boolean hard) {
            long now = System.currentTimeMillis();
            if (!shouldWarn(BudgetType.PARTICLE, now)) {
                return;
            }
            String severity = hard ? "hard" : "soft";
            String message = "Budget %s des particules dépassé dans l'arène %s (map=%s, mode=%s). Actuel: %d, Soft: %d, Hard: %d"
                    .formatted(severity, arenaId, mapId, mode, current, particlesSoftCap, particlesHardCap);
            logger.warn(message);
        }

        private boolean shouldWarn(BudgetType type, long now) {
            AtomicLong reference = warnTimestamps.get(type);
            long previous = reference.get();
            if (now - previous < WARN_INTERVAL_MS) {
                return false;
            }
            return reference.compareAndSet(previous, now);
        }

        private long limitFor(BudgetType type) {
            return switch (type) {
                case ENTITY -> maxEntities;
                case ITEM -> maxItems;
                case PROJECTILE -> maxProjectiles;
                case PARTICLE -> particlesHardCap;
            };
        }

        private BudgetSnapshot snapshot() {
            long particles;
            synchronized (particleLock) {
                particles = particleCounter.value();
            }
            return new BudgetSnapshot(
                    arenaId,
                    mapId,
                    mode,
                    active.get(BudgetType.ENTITY).value(),
                    pending.get(BudgetType.ENTITY).value(),
                    maxEntities,
                    active.get(BudgetType.ITEM).value(),
                    pending.get(BudgetType.ITEM).value(),
                    maxItems,
                    active.get(BudgetType.PROJECTILE).value(),
                    pending.get(BudgetType.PROJECTILE).value(),
                    maxProjectiles,
                    particles,
                    particlesSoftCap,
                    particlesHardCap);
        }
    }

    private static final class Counter {
        private final LongAdder additions = new LongAdder();
        private final LongAdder removals = new LongAdder();

        private void add(long amount) {
            if (amount <= 0) {
                return;
            }
            additions.add(amount);
        }

        private void remove(long amount) {
            if (amount <= 0) {
                return;
            }
            removals.add(amount);
        }

        private void reset() {
            additions.reset();
            removals.reset();
        }

        private long value() {
            long current = additions.sum() - removals.sum();
            return Math.max(0L, current);
        }
    }
}
