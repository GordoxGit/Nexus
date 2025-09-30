package com.heneria.nexus.listener;

import com.heneria.nexus.api.AntiSpawnKillService;
import com.heneria.nexus.api.ArenaHandle;
import com.heneria.nexus.api.ArenaPhase;
import com.heneria.nexus.api.ArenaService;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.util.NexusLogger;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Ensures players respawn at their configured team spawn point with protection.
 */
public final class PlayerRespawnListener implements Listener {

    private final NexusLogger logger;
    private final ArenaService arenaService;
    private final AntiSpawnKillService antiSpawnKillService;
    private final ExecutorManager executorManager;

    public PlayerRespawnListener(NexusLogger logger,
                                 ArenaService arenaService,
                                 AntiSpawnKillService antiSpawnKillService,
                                 ExecutorManager executorManager) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.arenaService = Objects.requireNonNull(arenaService, "arenaService");
        this.antiSpawnKillService = Objects.requireNonNull(antiSpawnKillService, "antiSpawnKillService");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<ArenaHandle> handleOptional = findArena(player);
        if (handleOptional.isEmpty()) {
            return;
        }
        ArenaHandle handle = handleOptional.get();
        if (handle.phase() != ArenaPhase.PLAYING) {
            return;
        }
        Optional<Location> spawnLocation = arenaService.findSpawnLocation(player);
        if (spawnLocation.isEmpty()) {
            logger.debug(() -> "Spawn introuvable pour " + player.getName() + " dans l'arène " + handle.id());
            return;
        }
        Location location = spawnLocation.get();
        if (location.getWorld() == null) {
            location.setWorld(event.getRespawnLocation().getWorld());
        }
        event.setRespawnLocation(location);
        executorManager.mainThread().runLater(() -> {
            if (player.isOnline()) {
                antiSpawnKillService.applyProtection(player);
            }
        }, 1L);
    }

    private Optional<ArenaHandle> findArena(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        String worldName = world.getName();
        return arenaService.instances().stream()
                .filter(handle -> worldName.equalsIgnoreCase(handle.id().toString())
                        || worldName.equalsIgnoreCase(handle.mapId()))
                .findFirst();
    }
}
