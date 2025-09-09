package fr.heneria.nexus.game.phase;

import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.NexusCore;
import fr.heneria.nexus.game.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Phase pendant laquelle un Cœur Nexus peut être détruit.
 */
public class DestructionPhase implements IPhase {

    private final JavaPlugin plugin;
    private final Team vulnerableTeam;
    private BossBar bossBar;
    private BukkitTask task;

    public DestructionPhase(JavaPlugin plugin, Team vulnerableTeam) {
        this.plugin = plugin;
        this.vulnerableTeam = vulnerableTeam;
    }

    @Override
    public void onStart(Match match) {
        bossBar = Bukkit.createBossBar("§c§lNEXUS VULNÉRABLE", BarColor.RED, BarStyle.SOLID);
        bossBar.setProgress(1.0);
        for (UUID playerId : match.getPlayers()) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                bossBar.addPlayer(p);
            }
        }
        match.broadcastMessage("§cLe Cœur Nexus de l'équipe " + vulnerableTeam.getTeamId() + " est maintenant vulnérable !");
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(match), 20L, 20L);
    }

    @Override
    public void onEnd(Match match) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    @Override
    public void tick(Match match) {
        NexusCore core = match.getNexusCore(vulnerableTeam.getTeamId());
        if (core == null || bossBar == null) {
            return;
        }
        bossBar.setProgress(Math.max(0, core.getHealth()) / core.getMaxHealth());
    }
}

