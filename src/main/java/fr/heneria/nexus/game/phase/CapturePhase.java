package fr.heneria.nexus.game.phase;

import fr.heneria.nexus.arena.model.ArenaGameObject;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase de capture d'une cellule d'énergie.
 */
public class CapturePhase implements IPhase {

    private final JavaPlugin plugin;
    private BukkitTask task;
    private EnergyCell energyCell;

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
        BossBar bossBar = Bukkit.createBossBar("Cellule d'Énergie", BarColor.WHITE, BarStyle.SOLID);
        for (UUID playerId : match.getPlayers()) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                bossBar.addPlayer(p);
            }
        }
        this.energyCell = new EnergyCell(obj.getLocation(), bossBar);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(match), 20L, 20L);
    }

    @Override
    public void onEnd(Match match) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (energyCell != null) {
            energyCell.getBossBar().removeAll();
        }
    }

    @Override
    public void tick(Match match) {
        if (energyCell == null) return;
        Location loc = energyCell.getLocation();
        List<Player> nearby = loc.getWorld().getPlayers().stream()
                .filter(p -> match.getPlayers().contains(p.getUniqueId()))
                .filter(p -> p.getLocation().distance(loc) <= 5)
                .toList();
        if (nearby.isEmpty()) {
            energyCell.getBossBar().setTitle("Cellule contestée");
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
            energyCell.getBossBar().setTitle("Cellule contestée");
            return;
        }
        double progress = energyCell.getCaptureProgress().merge(winningTeamId, 1D, Double::sum);
        energyCell.getBossBar().setColor(BarColor.GREEN);
        energyCell.getBossBar().setTitle("Équipe " + winningTeamId + " - " + (int) progress + "s");
        energyCell.getBossBar().setProgress(Math.min(progress, 60.0) / 60.0);
        if (progress >= 60) {
            match.broadcastMessage("§aL'équipe " + winningTeamId + " a capturé la Cellule d'Énergie !");
        }
    }
}
