package com.gordoxgit.henebrain.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.gordoxgit.henebrain.Henebrain;
import com.gordoxgit.henebrain.managers.GameManager;

/**
 * Handles player connection events.
 */
public class PlayerConnectionListener implements Listener {
    private final GameManager gameManager;

    public PlayerConnectionListener(Henebrain plugin) {
        this.gameManager = plugin.getGameManager();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        gameManager.removePlayerFromGame(event.getPlayer());
    }
}
