package com.gordoxgit.henebrain.listeners;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.Player;

import com.gordoxgit.henebrain.Henebrain;
import com.gordoxgit.henebrain.game.Game;
import com.gordoxgit.henebrain.game.GameState;

public class PointListener implements Listener {

    private final Henebrain plugin;

    public PointListener(Henebrain plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player);
        if (game == null || game.getState() != GameState.PLAYING) {
            return;
        }

        if (event.getTo() == null || game.getArena() == null || game.getArena().getPoint() == null) {
            return;
        }

        Location to = event.getTo().getBlock().getLocation();
        Location point = game.getArena().getPoint().getBlock().getLocation();
        if (to.equals(point)) {
            event.setCancelled(true);
            game.onPointScored(player);
        }
    }
}
