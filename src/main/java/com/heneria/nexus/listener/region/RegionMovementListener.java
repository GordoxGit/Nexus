package com.heneria.nexus.listener.region;

import com.heneria.nexus.api.region.RegionService;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps track of players entering or leaving regions to apply side effects.
 */
public final class RegionMovementListener implements Listener {

    private final RegionService regionService;

    public RegionMovementListener(RegionService regionService) {
        this.regionService = Objects.requireNonNull(regionService, "regionService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!hasMovedBlock(event)) {
            return;
        }
        regionService.handlePlayerMove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        regionService.handlePlayerLeave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        regionService.handlePlayerLeave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        regionService.handlePlayerMove(event.getPlayer());
    }

    private boolean hasMovedBlock(PlayerMoveEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return true;
        }
        return event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }
}
