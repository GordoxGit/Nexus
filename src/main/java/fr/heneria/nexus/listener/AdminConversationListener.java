package fr.heneria.nexus.listener;

import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Intercepte les messages de chat des administrateurs en conversation.
 */
public class AdminConversationListener implements Listener {

    private final AdminConversationManager conversationManager;
    private final JavaPlugin plugin;

    public AdminConversationListener(AdminConversationManager conversationManager, JavaPlugin plugin) {
        this.conversationManager = conversationManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!conversationManager.isInConversation(player)) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> conversationManager.handleResponse(player, message));
    }
}
