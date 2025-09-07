package fr.heneria.nexus.listener;

import fr.heneria.nexus.player.manager.PlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class PlayerConnectionListener implements Listener {

    private final PlayerManager playerManager;
    private final JavaPlugin plugin;

    public PlayerConnectionListener(PlayerManager playerManager, JavaPlugin plugin) {
        this.playerManager = playerManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        try {
            playerManager.loadProfile(uuid, name).get();
        } catch (InterruptedException | ExecutionException e) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cErreur lors du chargement de votre profil.");
            plugin.getLogger().severe("Failed to load profile for " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerManager.unloadProfile(event.getPlayer().getUniqueId());
    }
}
