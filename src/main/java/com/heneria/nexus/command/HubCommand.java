package com.heneria.nexus.command;

import com.heneria.nexus.api.TeleportService;
import com.heneria.nexus.api.TeleportService.TeleportResult;
import com.heneria.nexus.util.MessageFacade;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Simple command allowing players to return to the hub network.
 */
public final class HubCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final TeleportService teleportService;
    private final MessageFacade messageFacade;

    public HubCommand(JavaPlugin plugin, TeleportService teleportService, MessageFacade messageFacade) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.messageFacade = Objects.requireNonNull(messageFacade, "messageFacade");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageFacade.send(sender, "errors.player_only");
            return true;
        }
        if (!player.hasPermission("nexus.commands.hub")) {
            messageFacade.send(player, "errors.no_permission");
            return true;
        }
        messageFacade.send(player, "network.hub.teleporting");
        UUID playerId = player.getUniqueId();
        teleportService.returnToHub(playerId).whenComplete((result, throwable) ->
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        handleCompletion(player, result, throwable)));
        return true;
    }

    private void handleCompletion(Player player, TeleportResult result, Throwable throwable) {
        if (!player.isOnline()) {
            return;
        }
        if (throwable != null) {
            messageFacade.send(player, "network.hub.failed",
                    Placeholder.unparsed("reason", "Erreur interne"));
            return;
        }
        if (result == null) {
            messageFacade.send(player, "network.hub.failed",
                    Placeholder.unparsed("reason", "Réponse invalide"));
            return;
        }
        if (result.success()) {
            messageFacade.send(player, "network.hub.success");
            return;
        }
        String reason = result.message().isBlank() ? "Destination indisponible" : result.message();
        messageFacade.send(player, "network.hub.failed",
                Placeholder.unparsed("reason", reason));
    }
}
