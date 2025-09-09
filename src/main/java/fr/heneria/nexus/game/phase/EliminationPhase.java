package fr.heneria.nexus.game.phase;

import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Phase de fin où une équipe ne peut plus réapparaître.
 */
public class EliminationPhase implements IPhase {

    private final JavaPlugin plugin;
    private final Team eliminatedTeam;
    private BossBar bossBar;

    public EliminationPhase(JavaPlugin plugin, Team eliminatedTeam) {
        this.plugin = plugin;
        this.eliminatedTeam = eliminatedTeam;
    }

    @Override
    public void onStart(Match match) {
        match.getEliminatedTeamIds().add(eliminatedTeam.getTeamId());
        match.broadcastMessage("§4Le Cœur Nexus de l'équipe " + eliminatedTeam.getTeamId() + " a été détruit ! Plus de réapparitions pour eux !");
        bossBar = Bukkit.createBossBar("Éliminez les survivants !", BarColor.PURPLE, BarStyle.SOLID);
        for (UUID playerId : match.getPlayers()) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                bossBar.addPlayer(p);
            }
        }
    }

    @Override
    public void onEnd(Match match) {
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    @Override
    public void tick(Match match) {
        // Pas de logique périodique pour l'instant
    }
}

