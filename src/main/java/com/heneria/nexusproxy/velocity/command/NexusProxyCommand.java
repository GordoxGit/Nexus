package com.heneria.nexusproxy.velocity.command;

import com.heneria.nexusproxy.velocity.health.ServerAvailability;
import com.heneria.nexusproxy.velocity.health.ServerStatusRegistry;
import com.heneria.nexusproxy.velocity.health.ServerStatusSnapshot;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Simple administrative command displaying the health of Nexus servers.
 */
public final class NexusProxyCommand implements SimpleCommand {

    private final ServerStatusRegistry registry;

    public NexusProxyCommand(ServerStatusRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void execute(Invocation invocation) {
        String[] arguments = invocation.arguments();
        if (arguments.length == 0 || "status".equalsIgnoreCase(arguments[0])) {
            sendStatus(invocation.source());
            return;
        }
        invocation.source().sendMessage(Component.text("Usage: /nexusproxy status", NamedTextColor.RED));
    }

    private void sendStatus(com.velocitypowered.api.command.CommandSource source) {
        Collection<ServerStatusSnapshot> statuses = registry.snapshot();
        source.sendMessage(Component.text("=== Serveurs Nexus ===", NamedTextColor.GOLD));
        if (statuses.isEmpty()) {
            source.sendMessage(Component.text("Aucun ping reçu. Tous les serveurs sont considérés disponibles.",
                    NamedTextColor.GRAY));
            return;
        }
        for (ServerStatusSnapshot snapshot : statuses) {
            NamedTextColor color = colorFor(snapshot.availability());
            Component line = Component.text(snapshot.serverId() + ": ", NamedTextColor.WHITE)
                    .append(Component.text(snapshot.availability().name(), color))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(snapshot.playerCount() + "/" + snapshot.maxPlayers() + " joueurs",
                            NamedTextColor.GRAY))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.format(Locale.ROOT, "TPS %.2f", snapshot.tps()), NamedTextColor.AQUA));
            source.sendMessage(line);
        }
    }

    private NamedTextColor colorFor(ServerAvailability availability) {
        return switch (availability) {
            case LOBBY -> NamedTextColor.GREEN;
            case STARTING -> NamedTextColor.YELLOW;
            case IN_GAME -> NamedTextColor.RED;
            case ENDING -> NamedTextColor.GOLD;
            case OFFLINE -> NamedTextColor.DARK_RED;
            case UNKNOWN -> NamedTextColor.GRAY;
        };
    }
}
