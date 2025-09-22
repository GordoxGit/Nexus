package com.heneria.nexus.hologram;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gère la visibilité dynamique des hologrammes en fonction de la position des joueurs.
 */
public final class HologramVisibilityListener implements Listener {

    private final HoloService holoService;

    public HologramVisibilityListener(HoloService holoService) {
        this.holoService = Objects.requireNonNull(holoService, "holoService");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updateVisibility(player);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }
        Player player = event.getPlayer();
        updateVisibility(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hideAll(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        hideAll(player);
        updateVisibility(player);
    }

    private void updateVisibility(Player player) {
        Location playerLocation = player.getLocation();
        World playerWorld = playerLocation.getWorld();
        for (Hologram hologram : holoService.holograms()) {
            Location location = hologram.location();
            if (location == null) {
                hologram.hideFrom(player);
                continue;
            }
            World hologramWorld = location.getWorld();
            if (playerWorld == null || hologramWorld == null || !playerWorld.equals(hologramWorld)) {
                hologram.hideFrom(player);
                continue;
            }
            double range = hologram.viewRange();
            if (range <= 0D) {
                hologram.hideFrom(player);
                continue;
            }
            double distanceSquared = playerLocation.distanceSquared(location);
            double rangeSquared = range * range;
            if (distanceSquared <= rangeSquared) {
                hologram.showTo(player);
            } else {
                hologram.hideFrom(player);
            }
        }
    }

    private void hideAll(Player player) {
        for (Hologram hologram : holoService.holograms()) {
            hologram.hideFrom(player);
        }
    }
}

