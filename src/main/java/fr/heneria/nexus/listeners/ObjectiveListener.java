package fr.heneria.nexus.listeners;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.GameState;
import fr.heneria.nexus.game.objective.NexusCore;
import fr.heneria.nexus.game.team.GameTeam;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ObjectiveListener implements Listener {

    private final NexusPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 500; // 0.5 seconds

    public ObjectiveListener(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNexusInteract(PlayerInteractEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Location blockLoc = event.getClickedBlock().getLocation();

        // Find if this block is a Nexus
        NexusCore targetNexus = null;
        for (NexusCore nexus : plugin.getObjectiveManager().getNexusList()) {
            if (isSameBlock(nexus.getLocation(), blockLoc)) {
                targetNexus = nexus;
                break;
            }
        }

        if (targetNexus != null) {
            // Check cooldown
            long now = System.currentTimeMillis();
            if (cooldowns.containsKey(event.getPlayer().getUniqueId())) {
                long last = cooldowns.get(event.getPlayer().getUniqueId());
                if (now - last < COOLDOWN_MS) {
                    return; // Cooldown
                }
            }

            GameTeam attackerTeam = plugin.getTeamManager().getPlayerTeam(event.getPlayer());
            if (attackerTeam != null && attackerTeam != targetNexus.getOwner()) {
                targetNexus.damage(1.0, event.getPlayer()); // 1 damage per hit
                cooldowns.put(event.getPlayer().getUniqueId(), now);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        Location blockLoc = event.getBlock().getLocation();
        for (NexusCore nexus : plugin.getObjectiveManager().getNexusList()) {
            if (isSameBlock(nexus.getLocation(), blockLoc)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isSameBlock(Location loc1, Location loc2) {
        return loc1.getWorld().equals(loc2.getWorld()) &&
                loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() &&
                loc1.getBlockZ() == loc2.getBlockZ();
    }
}
