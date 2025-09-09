package fr.heneria.nexus.listener;

import fr.heneria.nexus.arena.model.ArenaGameObject;
import fr.heneria.nexus.game.manager.GameManager;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import fr.heneria.nexus.game.phase.GamePhase;
import fr.heneria.nexus.game.phase.TransportPhase;
import fr.heneria.nexus.game.phase.IPhase;
import fr.heneria.nexus.game.queue.QueueManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
        if (match.getCurrentPhase() == GamePhase.TRANSPORT) {
            IPhase phase = match.getPhaseManager().getPhase(GamePhase.TRANSPORT);
            if (phase instanceof TransportPhase transportPhase && uuid.equals(transportPhase.getCarrierId())) {
                player.removePotionEffect(PotionEffectType.GLOWING);
                match.broadcastMessage("§cLe porteur a été éliminé ! La Cellule d'Énergie a été réinitialisée.");
                match.getPhaseManager().transitionTo(match, GamePhase.CAPTURE);
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

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Match match = gameManager.getPlayerMatch(uuid);
        if (match == null || match.getCurrentPhase() != GamePhase.TRANSPORT) {
            return;
        }
        IPhase phase = match.getPhaseManager().getPhase(GamePhase.TRANSPORT);
        if (!(phase instanceof TransportPhase transportPhase)) {
            return;
        }
        if (transportPhase.getCarrierId() != null) {
            return;
        }
        ArenaGameObject cell = match.getArena().getGameObject("ENERGY_CELL", 1).orElse(null);
        if (cell == null || cell.getLocation() == null || event.getClickedBlock() == null) {
            return;
        }
        Location blockLoc = event.getClickedBlock().getLocation();
        if (!blockLoc.getWorld().equals(cell.getLocation().getWorld()) || blockLoc.distance(cell.getLocation()) > 1) {
            return;
        }
        Team team = match.getTeamOfPlayer(uuid);
        if (team == null || team.getTeamId() != transportPhase.getCapturingTeam().getTeamId()) {
            return;
        }
        transportPhase.setCarrierId(uuid);
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, true, false, false));
        match.broadcastMessage("§e" + player.getName() + " a ramassé la Cellule d'Énergie !");
    }
}
