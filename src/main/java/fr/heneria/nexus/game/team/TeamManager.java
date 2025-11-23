package fr.heneria.nexus.game.team;

import fr.heneria.nexus.NexusPlugin;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeamManager {

    private final NexusPlugin plugin;
    @Getter
    private final Map<UUID, GameTeam> playerTeams = new HashMap<>();

    public TeamManager(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    public void addPlayerToTeam(Player player, GameTeam team) {
        playerTeams.put(player.getUniqueId(), team);
        // We could add logic to teleport/gear up player here or fire an event
    }

    public void removePlayer(Player player) {
        playerTeams.remove(player.getUniqueId());
    }

    public GameTeam getPlayerTeam(Player player) {
        return playerTeams.get(player.getUniqueId());
    }
}
