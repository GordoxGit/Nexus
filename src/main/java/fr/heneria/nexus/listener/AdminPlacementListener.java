package fr.heneria.nexus.listener;

import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import fr.heneria.nexus.admin.placement.SpawnPlacementContext;
import fr.heneria.nexus.gui.admin.ArenaSpawnManagerGui;
import fr.heneria.nexus.arena.manager.ArenaManager;
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
        SpawnPlacementContext context = placementManager.getPlacementContext(player);
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            Location loc = block.getLocation().add(0.5, 1, 0.5);
            Location playerLoc = player.getLocation();
            loc.setYaw(playerLoc.getYaw());
            loc.setPitch(playerLoc.getPitch());
            context.arena().setSpawn(context.teamId(), context.spawnNumber(), loc);
            player.sendMessage("§aSpawn défini pour l'équipe §e" + context.teamId() + " §a, spawn §e" + context.spawnNumber() + "§a.");
            placementManager.endPlacementMode(player);
            Bukkit.getScheduler().runTask(plugin, () ->
                    new ArenaSpawnManagerGui(arenaManager, context.arena(), placementManager).open(player));
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            player.sendMessage("§cPlacement du spawn annulé.");
            placementManager.endPlacementMode(player);
            Bukkit.getScheduler().runTask(plugin, () ->
                    new ArenaSpawnManagerGui(arenaManager, context.arena(), placementManager).open(player));
        }
    }
}

