package com.heneria.nexus.service.core.region;

import com.heneria.nexus.api.MapDefinition;
import com.heneria.nexus.api.region.Region;
import com.heneria.nexus.api.region.RegionFlag;
import com.heneria.nexus.api.region.RegionService;
import com.heneria.nexus.scheduler.GamePhase;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.util.NexusLogger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;

/**
 * Default in-memory implementation of {@link RegionService}.
 */
public final class RegionServiceImpl implements RegionService {

    private static final String EFFECT_TASK_ID = "regions-effects";
    private static final EnumSet<GamePhase> EFFECT_PHASES = EnumSet.allOf(GamePhase.class);
    private static final long EFFECT_TASK_INTERVAL = 20L;
    private static final Map<RegionFlag, Object> BASE_DEFAULTS = Map.of(
            RegionFlag.PVP_ENABLED, Boolean.TRUE,
            RegionFlag.BUILD_ALLOWED, Boolean.TRUE,
            RegionFlag.FALL_DAMAGE, Boolean.TRUE
    );

    private final NexusLogger logger;
    private final RingScheduler ringScheduler;
    private final ConcurrentMap<UUID, ArenaRegionState> arenas = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> worldToArena = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Map<PotionEffectType, PotionEffect>> playerEffects = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> playerArena = new ConcurrentHashMap<>();

    public RegionServiceImpl(NexusLogger logger, RingScheduler ringScheduler) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.ringScheduler = Objects.requireNonNull(ringScheduler, "ringScheduler");
    }

    @Override
    public void registerArena(UUID arenaId, MapDefinition definition) {
        Objects.requireNonNull(arenaId, "arenaId");
        Objects.requireNonNull(definition, "definition");
        List<Region> regions = definition.regions();
        Map<RegionFlag, Object> defaults = mergeDefaults(definition.regionDefaults());
        ArenaRegionState state = new ArenaRegionState(arenaId, definition.id(), defaults, sortRegions(regions));
        arenas.put(arenaId, state);
        worldToArena.put(definition.id().toLowerCase(Locale.ROOT), arenaId);
        worldToArena.put(arenaId.toString().toLowerCase(Locale.ROOT), arenaId);
        logger.debug(() -> "Régions enregistrées pour l'arène " + arenaId + " -> " + regions.size() + " zone(s)");
    }

    @Override
    public void unregisterArena(UUID arenaId) {
        Objects.requireNonNull(arenaId, "arenaId");
        ArenaRegionState state = arenas.remove(arenaId);
        if (state == null) {
            return;
        }
        state.worldAliases().forEach(worldToArena::remove);
        List<UUID> affectedPlayers = playerArena.entrySet().stream()
                .filter(entry -> arenaId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        affectedPlayers.forEach(this::clearPlayerEffects);
        logger.debug(() -> "Régions déchargées pour l'arène " + arenaId);
    }

    @Override
    public Collection<Region> getRegionsForArena(UUID arenaId) {
        ArenaRegionState state = arenas.get(arenaId);
        if (state == null) {
            return List.of();
        }
        return state.regions();
    }

    @Override
    public Map<RegionFlag, Object> getFlagsAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Map.of();
        }
        UUID arenaId = worldToArena.get(location.getWorld().getName().toLowerCase(Locale.ROOT));
        if (arenaId == null) {
            return Map.of();
        }
        ArenaRegionState state = arenas.get(arenaId);
        if (state == null) {
            return Map.of();
        }
        Map<RegionFlag, Object> resolved = new EnumMap<>(RegionFlag.class);
        resolved.putAll(state.defaults());
        List<Region> stack = resolveRegions(state.regions(), location);
        for (Region region : stack) {
            region.flags().forEach(resolved::put);
        }
        return Map.copyOf(resolved);
    }

    @Override
    public void handlePlayerMove(Player player) {
        Objects.requireNonNull(player, "player");
        updatePlayerState(player, false);
    }

    @Override
    public void handlePlayerLeave(Player player) {
        Objects.requireNonNull(player, "player");
        clearPlayerEffects(player.getUniqueId());
        playerArena.remove(player.getUniqueId());
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> start() {
        ringScheduler.registerTask(EFFECT_TASK_ID, EFFECT_TASK_INTERVAL, EFFECT_PHASES, this::refreshPlayerStates);
        return RegionService.super.start();
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> stop() {
        ringScheduler.unregisterTask(EFFECT_TASK_ID);
        arenas.clear();
        worldToArena.clear();
        playerArena.keySet().forEach(this::clearPlayerEffects);
        playerArena.clear();
        playerEffects.clear();
        return RegionService.super.stop();
    }

    private Map<RegionFlag, Object> mergeDefaults(Map<RegionFlag, Object> mapDefaults) {
        EnumMap<RegionFlag, Object> merged = new EnumMap<>(RegionFlag.class);
        merged.putAll(BASE_DEFAULTS);
        if (mapDefaults != null) {
            merged.putAll(mapDefaults);
        }
        return Map.copyOf(merged);
    }

    private List<Region> sortRegions(Collection<Region> regions) {
        if (regions == null || regions.isEmpty()) {
            return List.of();
        }
        return regions.stream()
                .sorted((a, b) -> {
                    int volumeComparison = Double.compare(a.volume(), b.volume());
                    if (volumeComparison != 0) {
                        return volumeComparison;
                    }
                    return a.id().compareToIgnoreCase(b.id());
                })
                .collect(Collectors.toUnmodifiableList());
    }

    private List<Region> resolveRegions(List<Region> regions, Location location) {
        if (regions == null || regions.isEmpty()) {
            return List.of();
        }
        return regions.stream()
                .filter(region -> contains(region.bounds(), location))
                .toList();
    }

    private boolean contains(BoundingBox bounds, Location location) {
        return bounds.contains(location.getX(), location.getY(), location.getZ());
    }

    private void updatePlayerState(Player player, boolean force) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            clearPlayerEffects(player.getUniqueId());
            playerArena.remove(player.getUniqueId());
            return;
        }
        String worldName = location.getWorld().getName().toLowerCase(Locale.ROOT);
        UUID arenaId = worldToArena.get(worldName);
        if (arenaId == null) {
            clearPlayerEffects(player.getUniqueId());
            playerArena.remove(player.getUniqueId());
            return;
        }
        Map<RegionFlag, Object> flags = getFlagsAt(location);
        playerArena.put(player.getUniqueId(), arenaId);
        applyEffects(player, flags, force);
    }

    private void refreshPlayerStates() {
        for (UUID playerId : List.copyOf(playerArena.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                clearPlayerEffects(playerId);
                playerArena.remove(playerId);
                continue;
            }
            updatePlayerState(player, true);
        }
    }

    private void applyEffects(Player player, Map<RegionFlag, Object> flags, boolean force) {
        Object raw = flags.get(RegionFlag.EFFECTS_APPLY);
        Collection<PotionEffect> desired = resolveEffects(raw);
        UUID playerId = player.getUniqueId();
        Map<PotionEffectType, PotionEffect> active = playerEffects.computeIfAbsent(playerId,
                ignored -> new LinkedHashMap<>());
        if (desired.isEmpty()) {
            if (!active.isEmpty()) {
                active.keySet().forEach(player::removePotionEffect);
                active.clear();
            }
            return;
        }
        Map<PotionEffectType, PotionEffect> desiredMap = desired.stream()
                .collect(Collectors.toMap(
                        PotionEffect::getType,
                        effect -> effect,
                        (first, second) -> second,
                        LinkedHashMap::new));
        boolean changed = force;
        for (PotionEffect effect : desired) {
            PotionEffect current = active.get(effect.getType());
            if (!effect.equals(current) || force) {
                player.addPotionEffect(effect, true);
                active.put(effect.getType(), effect);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        List<PotionEffectType> toRemove = active.keySet().stream()
                .filter(type -> !desiredMap.containsKey(type))
                .toList();
        toRemove.forEach(type -> {
            player.removePotionEffect(type);
            active.remove(type);
        });
    }

    private Collection<PotionEffect> resolveEffects(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> collection) {
            List<PotionEffect> effects = new ArrayList<>();
            for (Object element : collection) {
                effects.addAll(resolveEffects(element));
            }
            return effects;
        }
        if (raw instanceof Map<?, ?> map) {
            List<PotionEffect> effects = new ArrayList<>();
            map.forEach((key, value) -> {
                PotionEffectType type = PotionEffectType.getByName(String.valueOf(key).toUpperCase(Locale.ROOT));
                if (type == null) {
                    return;
                }
                PotionEffect effect = buildEffect(type, value);
                if (effect != null) {
                    effects.add(effect);
                }
            });
            return effects;
        }
        if (raw instanceof String string) {
            PotionEffectType type = PotionEffectType.getByName(string.toUpperCase(Locale.ROOT));
            if (type == null) {
                return List.of();
            }
            PotionEffect effect = new PotionEffect(type, 100, 0, false, true, true);
            return List.of(effect);
        }
        if (raw instanceof PotionEffect effect) {
            return List.of(effect);
        }
        return List.of();
    }

    private PotionEffect buildEffect(PotionEffectType type, Object value) {
        if (value instanceof Number number) {
            int amplifier = Math.max(0, number.intValue());
            return new PotionEffect(type, 100, amplifier, false, true, true);
        }
        if (value instanceof Map<?, ?> map) {
            int amplifier = getInt(map.get("amplifier"), 0);
            int duration = getInt(map.get("duration"), 100);
            boolean ambient = getBoolean(map.get("ambient"), false);
            boolean particles = getBoolean(map.get("particles"), true);
            boolean icon = getBoolean(map.get("icon"), true);
            return new PotionEffect(type, Math.max(1, duration), Math.max(0, amplifier), ambient, particles, icon);
        }
        if (value instanceof String string) {
            try {
                int amplifier = Integer.parseInt(string.trim());
                return new PotionEffect(type, 100, Math.max(0, amplifier), false, true, true);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int getInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean getBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string.trim());
        }
        return fallback;
    }

    private void clearPlayerEffects(UUID playerId) {
        Map<PotionEffectType, PotionEffect> effects = playerEffects.remove(playerId);
        if (effects == null || effects.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        effects.keySet().forEach(player::removePotionEffect);
    }

    private record ArenaRegionState(UUID arenaId,
                                    String mapId,
                                    Map<RegionFlag, Object> defaults,
                                    List<Region> regions) {

        Collection<String> worldAliases() {
            return List.of(arenaId.toString().toLowerCase(Locale.ROOT), mapId.toLowerCase(Locale.ROOT));
        }
    }
}
