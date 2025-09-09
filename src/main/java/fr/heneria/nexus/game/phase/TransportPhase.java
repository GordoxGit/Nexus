package fr.heneria.nexus.game.phase;

import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import fr.heneria.nexus.game.model.NexusCore;
import fr.heneria.nexus.game.phase.GamePhase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Phase de transport de la Cellule d'Énergie.
 */
public class TransportPhase implements IPhase {

    private final JavaPlugin plugin;
    private final Team capturingTeam;
    private UUID carrierId;
    private BukkitTask task;
    private BossBar escortBar;
    private BossBar huntBar;

    public TransportPhase(JavaPlugin plugin, Team capturingTeam) {
        this.plugin = plugin;
        this.capturingTeam = capturingTeam;
    }

    public UUID getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(UUID carrierId) {
        this.carrierId = carrierId;
    }

    public Team getCapturingTeam() {
        return capturingTeam;
    }

    @Override
    public void onStart(Match match) {
        escortBar = Bukkit.createBossBar("Escortez le porteur !", BarColor.GREEN, BarStyle.SOLID);
        huntBar = Bukkit.createBossBar("Éliminez le porteur !", BarColor.RED, BarStyle.SOLID);
        for (UUID playerId : match.getPlayers()) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                Team team = match.getTeamOfPlayer(playerId);
                if (team != null && team.getTeamId() == capturingTeam.getTeamId()) {
                    escortBar.addPlayer(p);
                } else {
                    huntBar.addPlayer(p);
                }
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(match), 20L, 20L);
    }

    @Override
    public void onEnd(Match match) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (escortBar != null) {
            escortBar.removeAll();
        }
        if (huntBar != null) {
            huntBar.removeAll();
        }
        if (carrierId != null) {
            Player carrier = Bukkit.getPlayer(carrierId);
            if (carrier != null) {
                carrier.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        carrierId = null;
    }

    @Override
    public void tick(Match match) {
        if (carrierId == null) {
            return;
        }
        Player carrier = Bukkit.getPlayer(carrierId);
        if (carrier == null) {
            return;
        }
        Location carrierLoc = carrier.getLocation();
        double nearest = Double.MAX_VALUE;
        for (NexusCore core : match.getNexusCores().values()) {
            if (core.getTeam().getTeamId() == capturingTeam.getTeamId()) {
                continue;
            }
            Location coreLoc = core.getLocation();
            if (coreLoc == null || !coreLoc.getWorld().equals(carrierLoc.getWorld())) {
                continue;
            }
            double distance = carrierLoc.distance(coreLoc);
            if (distance < nearest) {
                nearest = distance;
            }
            if (distance < 3) {
                carrier.removePotionEffect(PotionEffectType.GLOWING);
                int targetTeamId = core.getTeam().getTeamId();
                NexusCore nexusCore = match.addSurcharge(targetTeamId);
                match.broadcastMessage("§aLe Cœur Nexus de l'équipe " + targetTeamId + " a été surchargé !");
                carrierId = null;
                if (nexusCore != null && nexusCore.isVulnerable()) {
                    Team targetTeam = match.getTeams().get(targetTeamId);
                    match.getPhaseManager().transitionTo(match, GamePhase.DESTRUCTION, targetTeam);
                } else {
                    match.getPhaseManager().transitionTo(match, GamePhase.CAPTURE);
                }
                break;
            }
        }
        if (nearest != Double.MAX_VALUE) {
            int dist = (int) Math.ceil(nearest);
            carrier.sendActionBar("§aNexus ennemi à §e" + dist + " blocs");
        }
    }
}
