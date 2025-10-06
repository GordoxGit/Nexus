package com.heneria.nexus.service.core.nexus;

import com.heneria.nexus.api.ArenaHandle;
import com.heneria.nexus.api.map.MapBlueprint;
import com.heneria.nexus.api.map.MapBlueprint.MapNexus;
import com.heneria.nexus.api.map.MapBlueprint.MapTeam;
import com.heneria.nexus.api.map.MapBlueprint.MapVector;
import com.heneria.nexus.hologram.HoloService;
import com.heneria.nexus.hologram.Hologram;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.NexusLogger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.text.Component;

/**
 * Central manager keeping track of nexus cores for every arena.
 */
public final class NexusManager implements LifecycleAware {

    private final NexusLogger logger;
    private final HoloService holoService;
    private final List<NexusListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<LocationKey, NexusCore> coresByLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, NexusCore>> coresByArena = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> overloadsByArena = new ConcurrentHashMap<>();

    public NexusManager(NexusLogger logger, HoloService holoService) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.holoService = Objects.requireNonNull(holoService, "holoService");
    }

    @Override
    public synchronized CompletableFuture<Void> stop() {
        clearAll();
        return CompletableFuture.completedFuture(null);
    }

    public void registerListener(NexusListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void unregisterListener(NexusListener listener) {
        listeners.remove(listener);
    }

    public void initializeArena(ArenaHandle handle, World world, MapBlueprint blueprint) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(world, "world");
        if (blueprint == null || blueprint.teams() == null) {
            logger.warn("Arène " + handle.id() + " : blueprint inexploitable, nexus non initialisé");
            return;
        }
        clearArena(handle);
        List<MapTeam> teams = blueprint.teams();
        if (teams.isEmpty()) {
            logger.warn("Arène " + handle.id() + " : aucune équipe définie, nexus ignoré");
            return;
        }
        Map<String, NexusCore> byTeam = new ConcurrentHashMap<>();
        Map<String, Integer> overloads = new ConcurrentHashMap<>();
        for (MapTeam teamDefinition : teams) {
            if (teamDefinition == null) {
                continue;
            }
            MapNexus nexusDefinition = teamDefinition.nexus();
            if (nexusDefinition == null || nexusDefinition.position() == null) {
                logger.warn("Arène " + handle.id() + " : nexus non défini pour l'équipe " + teamDefinition.id());
                continue;
            }
            Location location = toLocation(world, nexusDefinition.position());
            if (location == null) {
                logger.warn("Arène " + handle.id() + " : coordonnées de nexus invalides pour " + teamDefinition.id());
                continue;
            }
            Block block = location.getBlock();
            if (block.getType() != Material.BEACON) {
                logger.debug(() -> "Arène " + handle.id() + " : nexus " + teamDefinition.id()
                        + " ne repose pas sur un beacon (" + block.getType() + ")");
            }
            Team scoreboardTeam = resolveOrCreateTeam(teamDefinition);
            if (scoreboardTeam == null) {
                logger.warn("Arène " + handle.id() + " : impossible de créer le team scoreboard pour " + teamDefinition.id());
                continue;
            }
            String displayName = teamDefinition.displayName() == null || teamDefinition.displayName().isBlank()
                    ? teamDefinition.id()
                    : teamDefinition.displayName();
            String hologramId = buildHologramId(handle.id(), teamDefinition.id());
            Location hologramLocation = location.clone().add(0.5D, 2.25D, 0.5D);
            Hologram hologram;
            try {
                hologram = holoService.createHologram(hologramId, hologramLocation, List.of(
                        "<aqua>Nexus " + displayName,
                        "<yellow>PV : " + Optional.ofNullable(nexusDefinition.hitPoints()).orElse(50),
                        "<gray>État : Protégé"
                ));
            } catch (IllegalArgumentException exception) {
                logger.warn("Impossible de créer l'hologramme du nexus " + teamDefinition.id(), exception);
                continue;
            }
            NexusCore core = new NexusCore(handle,
                    scoreboardTeam,
                    teamDefinition.id(),
                    displayName,
                    location,
                    Optional.ofNullable(nexusDefinition.hitPoints()).orElse(50),
                    hologram);
            coresByLocation.put(LocationKey.from(location), core);
            byTeam.put(normalize(teamDefinition.id()), core);
            overloads.put(normalize(teamDefinition.id()), 0);
        }
        if (byTeam.isEmpty()) {
            logger.warn("Arène " + handle.id() + " : aucun nexus initialisé");
            return;
        }
        coresByArena.put(handle.id(), byTeam);
        overloadsByArena.put(handle.id(), overloads);
    }

    public void clearArena(ArenaHandle handle) {
        if (handle == null) {
            return;
        }
        Map<String, NexusCore> cores = coresByArena.remove(handle.id());
        overloadsByArena.remove(handle.id());
        if (cores == null || cores.isEmpty()) {
            return;
        }
        for (NexusCore core : cores.values()) {
            coresByLocation.remove(LocationKey.from(core.blockLocation()));
            holoService.removeHologram(buildHologramId(handle.id(), core.teamId()));
            core.dispose();
        }
    }

    private void clearAll() {
        Map<LocationKey, NexusCore> snapshot = Map.copyOf(coresByLocation);
        snapshot.forEach((key, core) -> {
            holoService.removeHologram(buildHologramId(core.arena().id(), core.teamId()));
            core.dispose();
        });
        coresByLocation.clear();
        coresByArena.clear();
        overloadsByArena.clear();
    }

    public void handleBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (block == null || block.getType() != Material.BEACON) {
            return;
        }
        NexusCore core = coresByLocation.get(LocationKey.from(block.getLocation()));
        if (core == null) {
            return;
        }
        event.setCancelled(true);
        event.setInstaBreak(false);
        if (core.state() == NexusState.PROTECTED || core.destroyed()) {
            return;
        }
        Player player = event.getPlayer();
        Team playerTeam = resolveTeam(player);
        if (playerTeam != null && playerTeam.equals(core.team())) {
            return;
        }
        int damage = core.state() == NexusState.CRITICAL ? 2 : 1;
        boolean destroyed = core.applyDamage(damage);
        if (destroyed) {
            core.playDestructionEffects();
            LocationKey key = LocationKey.from(block.getLocation());
            coresByLocation.remove(key);
            Map<String, NexusCore> byTeam = coresByArena.get(core.arena().id());
            if (byTeam != null) {
                byTeam.remove(core.teamId());
            }
            Map<String, Integer> overloads = overloadsByArena.get(core.arena().id());
            if (overloads != null) {
                overloads.remove(core.teamId());
            }
            listeners.forEach(listener -> listener.onNexusDestroyed(core));
            return;
        }
        if (core.state() == NexusState.CRITICAL) {
            core.showCriticalEffect();
        }
    }

    public void applyOverload(ArenaHandle handle, String teamId) {
        if (handle == null || teamId == null) {
            return;
        }
        Map<String, NexusCore> cores = coresByArena.get(handle.id());
        if (cores == null || cores.isEmpty()) {
            return;
        }
        String normalized = normalize(teamId);
        NexusCore core = cores.get(normalized);
        if (core == null) {
            return;
        }
        if (core.destroyed()) {
            return;
        }
        Map<String, Integer> overloads = overloadsByArena.computeIfAbsent(handle.id(), ignored -> new ConcurrentHashMap<>());
        int level = overloads.merge(normalized, 1, Integer::sum);
        if (level <= 0) {
            level = 1;
        }
        switch (level) {
            case 1 -> {
                core.setState(NexusState.EXPOSED);
                core.showExposureEffect();
            }
            case 2 -> {
                core.setState(NexusState.CRITICAL);
                core.showCriticalEffect();
            }
            default -> core.showCriticalEffect();
        }
    }

    private Team resolveOrCreateTeam(MapTeam teamDefinition) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return null;
        }
        Scoreboard scoreboard = manager.getMainScoreboard();
        if (scoreboard == null) {
            return null;
        }
        String teamId = teamDefinition.id();
        Team team = scoreboard.getTeam(teamId);
        if (team == null) {
            try {
                team = scoreboard.registerNewTeam(teamId);
            } catch (IllegalArgumentException exception) {
                logger.warn("Impossible de créer l'équipe scoreboard '" + teamId + "'", exception);
                return null;
            }
            if (teamDefinition.displayName() != null) {
                team.displayName(Component.text(teamDefinition.displayName()));
            }
        }
        return team;
    }

    private Team resolveTeam(Player player) {
        if (player == null) {
            return null;
        }
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard != null) {
            Team team = scoreboard.getEntryTeam(player.getName());
            if (team != null) {
                return team;
            }
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Scoreboard main = manager.getMainScoreboard();
            if (main != null) {
                return main.getEntryTeam(player.getName());
            }
        }
        return null;
    }

    private String buildHologramId(UUID arenaId, String teamId) {
        return "nexus-" + arenaId + "-" + normalize(teamId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private Location toLocation(World world, MapVector vector) {
        if (world == null || vector == null || !vector.hasCoordinates()) {
            return null;
        }
        return new Location(world, vector.x(), vector.y(), vector.z());
    }

    private record LocationKey(UUID worldId, int x, int y, int z) {
        private static LocationKey from(Location location) {
            return new LocationKey(location.getWorld() != null ? location.getWorld().getUID() : new UUID(0L, 0L),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());
        }
    }

    public interface NexusListener {
        void onNexusDestroyed(NexusCore core);
    }
}
