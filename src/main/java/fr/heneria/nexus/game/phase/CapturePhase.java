package fr.heneria.nexus.game.phase;

import fr.heneria.nexus.arena.model.ArenaGameObject;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import fr.heneria.nexus.game.hologram.HologramManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase de capture d'une cellule d'énergie.
 */
public class CapturePhase implements IPhase {

    private final JavaPlugin plugin;
    private BukkitTask task;
    private EnergyCell energyCell;
    private final HologramManager hologramManager = new HologramManager();

    public CapturePhase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onStart(Match match) {
        ArenaGameObject obj = match.getArena().getGameObject("ENERGY_CELL", 1).orElse(null);
        if (obj == null || obj.getLocation() == null) {
            match.broadcastMessage("§cAucune Cellule d'Énergie définie pour cette arène.");
            return;
        }
        this.energyCell = new EnergyCell(obj.getLocation());
        hologramManager.createCaptureHologram(obj.getLocation());
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(match), 20L, 20L);
    }

    @Override
    public void onEnd(Match match) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        hologramManager.removeCaptureHologram();
    }

    @Override
    public void tick(Match match) {
        if (energyCell == null) return;
        Location loc = energyCell.getLocation();
        List<Player> nearby = loc.getWorld().getPlayers().stream()
                .filter(p -> match.getPlayers().contains(p.getUniqueId()))
                .filter(p -> p.getLocation().distance(loc) <= 5)
                .toList();
        Map<Integer, Double> progresses = new HashMap<>();
        for (Team team : match.getTeams().values()) {
            progresses.put(team.getTeamId(), energyCell.getCaptureProgress().getOrDefault(team.getTeamId(), 0D));
        }
        if (nearby.isEmpty()) {
            hologramManager.updateCaptureHologram(progresses, -1, true);
            return;
        }
        Map<Integer, Long> counts = new HashMap<>();
        for (Player p : nearby) {
            Team team = match.getTeamOfPlayer(p.getUniqueId());
            if (team != null) {
                counts.merge(team.getTeamId(), 1L, Long::sum);
            }
        }
        int total = nearby.size();
        int winningTeamId = -1;
        long max = 0;
        for (Map.Entry<Integer, Long> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                winningTeamId = entry.getKey();
            }
        }
        if (winningTeamId == -1 || max <= total - max) {
            hologramManager.updateCaptureHologram(progresses, -1, true);
            return;
        }
        double progress = energyCell.getCaptureProgress().merge(winningTeamId, 1D, Double::sum);
        progresses.put(winningTeamId, progress);
        hologramManager.updateCaptureHologram(progresses, winningTeamId, false);
        if (progress >= 60) {
            match.broadcastMessage("§aL'équipe " + winningTeamId + " a capturé la Cellule d'Énergie !");
            Team team = match.getTeams().get(winningTeamId);
            if (team != null) {
                match.getPhaseManager().transitionTo(match, GamePhase.TRANSPORT, team);
            }
        }
    }
}
