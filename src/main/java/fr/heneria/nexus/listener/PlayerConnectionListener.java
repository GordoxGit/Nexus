package fr.heneria.nexus.listener;

import fr.heneria.nexus.player.manager.PlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerConnectionListener implements Listener {

    private final PlayerManager playerManager;
    private final JavaPlugin plugin; // CORRECTION: Ajout du champ pour le plugin

    // CORRECTION: Le constructeur accepte maintenant le plugin
    public PlayerConnectionListener(PlayerManager playerManager, JavaPlugin plugin) {
        this.playerManager = playerManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        plugin.getLogger().info("Chargement du profil pour " + event.getName() + "...");
        playerManager.loadPlayerProfile(event.getUniqueId(), event.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getLogger().info("Sauvegarde du profil pour " + event.getPlayer().getName() + "...");
        playerManager.unloadPlayerProfile(event.getPlayer().getUniqueId());
    }
}
