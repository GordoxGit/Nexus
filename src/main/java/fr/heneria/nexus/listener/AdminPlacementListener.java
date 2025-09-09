package fr.heneria.nexus.listener;

import fr.heneria.nexus.admin.placement.*;
import fr.heneria.nexus.arena.manager.ArenaManager; // <-- CORRECTION ICI
import fr.heneria.nexus.gui.admin.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Intercepte les clics des administrateurs en mode de placement.
 */
public class AdminPlacementListener implements Listener {

    private final AdminPlacementManager placementManager;
    private final ArenaManager arenaManager;
    private final JavaPlugin plugin;

    public AdminPlacementListener(AdminPlacementManager placementManager, ArenaManager arenaManager, JavaPlugin plugin) {
        this.placementManager = placementManager;
        this.arenaManager = arenaManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!placementManager.isInPlacementMode(player)) {
            return;
        }
        event.setCancelled(true);
        PlacementContext context = placementManager.getPlacementContext(player);
        Action action = event.getAction();
        if (context instanceof SpawnPlacementContext spawnCtx) {
            if (action == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
                Block block = event.getClickedBlock();
                Location loc = block.getLocation().add(0.5, 1, 0.5);
                Location playerLoc = player.getLocation();
                loc.setYaw(playerLoc.getYaw());
                loc.setPitch(playerLoc.getPitch());
                spawnCtx.arena().setSpawn(spawnCtx.teamId(), spawnCtx.spawnNumber(), loc);
                player.sendMessage("§aSpawn défini pour l'équipe §e" + spawnCtx.teamId() + " §a, spawn §e" + spawnCtx.spawnNumber() + "§a.");
                placementManager.endPlacementMode(player);
                Bukkit.getScheduler().runTask(plugin, () ->
                        new ArenaSpawnManagerGui(arenaManager, spawnCtx.arena(), placementManager).open(player));
            } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                player.sendMessage("§cPlacement du spawn annulé.");
                placementManager.endPlacementMode(player);
                Bukkit.getScheduler().runTask(plugin, () ->
                        new ArenaSpawnManagerGui(arenaManager, spawnCtx.arena(), placementManager).open(player));
            }
        } else if (context instanceof GameObjectPlacementContext objCtx) {
            if (action == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
                Block block = event.getClickedBlock();
                Location loc = block.getLocation().add(0.5, 1, 0.5);
                Location playerLoc = player.getLocation();
                loc.setYaw(playerLoc.getYaw());
                loc.setPitch(playerLoc.getPitch());
                objCtx.gameObject().setLocation(loc);
                player.sendMessage("§aEmplacement défini pour " + objCtx.gameObject().getObjectType() + " "+ objCtx.gameObject().getObjectIndex() +".");
                placementManager.endPlacementMode(player);
                Bukkit.getScheduler().runTask(plugin, () ->
                        new ArenaGameObjectGui(arenaManager, objCtx.arena(), placementManager).open(player));
            } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                player.sendMessage("§cPlacement annulé.");
                placementManager.endPlacementMode(player);
                Bukkit.getScheduler().runTask(plugin, () ->
                        new ArenaGameObjectGui(arenaManager, objCtx.arena(), placementManager).open(player));
            }
        }
    }
}
