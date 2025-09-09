package fr.heneria.nexus.listener;

import fr.heneria.nexus.game.manager.GameManager;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import fr.heneria.nexus.game.queue.QueueManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public class GameListener implements Listener {

    private final GameManager gameManager;
    private final JavaPlugin plugin;
    private final QueueManager queueManager;

    public GameListener(GameManager gameManager, JavaPlugin plugin, QueueManager queueManager) {
        this.gameManager = gameManager;
        this.plugin = plugin;
        this.queueManager = queueManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        queueManager.leaveQueue(event.getPlayer());
        Match match = gameManager.getPlayerMatch(uuid);
        if (match != null) {
            match.broadcastMessage(event.getPlayer().getName() + " a quitté la partie.");
            gameManager.removePlayer(uuid);
            long remainingTeams = match.getTeams().values().stream().filter(t -> !t.getPlayers().isEmpty()).count();
            if (remainingTeams <= 1) {
                int winningTeamId = match.getTeams().values().stream()
                        .filter(t -> !t.getPlayers().isEmpty())
                        .findFirst().map(Team::getTeamId).orElse(0);
                gameManager.endMatch(match, winningTeamId);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        Match match = gameManager.getPlayerMatch(uuid);
        if (match == null) {
            return;
        }
        event.getDrops().clear();
        event.setDeathMessage(null);

        match.incrementDeath(uuid);
        Player killer = player.getKiller();
        if (killer != null) {
            match.incrementKill(killer.getUniqueId());
        }

        Team team = match.getTeamOfPlayer(uuid);
        Location spawn = null;
        if (team != null) {
            Map<Integer, Location> spawns = match.getArena().getSpawns().get(team.getTeamId());
            if (spawns != null && !spawns.isEmpty()) {
                spawn = spawns.values().iterator().next();
            }
        }
        Location finalSpawn = spawn;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.spigot().respawn();
            if (finalSpawn != null) {
                player.teleport(finalSpawn);
            }
        }, 1L);
    }
}
