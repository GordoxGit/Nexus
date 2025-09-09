package fr.heneria.nexus.listener;

import fr.heneria.nexus.arena.model.ArenaGameObject;
import fr.heneria.nexus.game.manager.GameManager;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import fr.heneria.nexus.game.model.NexusCore;
import fr.heneria.nexus.game.model.MatchType;
import fr.heneria.nexus.game.model.GameState;
import fr.heneria.nexus.game.phase.GamePhase;
import fr.heneria.nexus.game.phase.TransportPhase;
import fr.heneria.nexus.game.phase.IPhase;
import fr.heneria.nexus.game.queue.QueueManager;
import fr.heneria.nexus.sanction.SanctionManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

public class GameListener implements Listener {

    private final GameManager gameManager;
    private final JavaPlugin plugin;
    private final QueueManager queueManager;
    private final SanctionManager sanctionManager;

    public GameListener(GameManager gameManager, JavaPlugin plugin, QueueManager queueManager, SanctionManager sanctionManager) {
        this.gameManager = gameManager;
        this.plugin = plugin;
        this.queueManager = queueManager;
        this.sanctionManager = sanctionManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        queueManager.leaveQueue(event.getPlayer());
        Match match = gameManager.getPlayerMatch(uuid);
        if (match != null) {
            if (match.getMatchType() == MatchType.RANKED && match.getState() == GameState.IN_PROGRESS) {
                sanctionManager.penalizeLeaver(event.getPlayer(), match);
            }
            match.broadcastMessage(event.getPlayer().getName() + " a quitté la partie.");
            gameManager.removePlayer(uuid);
            long remainingTeams = match.getTeams().values().stream().filter(t -> !t.getPlayers().isEmpty()).count();
            if (remainingTeams <= 1) {
                int winningTeamId = match.getTeams().values().stream()
                        .filter(t -> !t.getPlayers().isEmpty())
                        .findFirst().map(Team::getTeamId).orElse(0);
                gameManager.endRound(match, winningTeamId);
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
        if (team != null && match.getEliminatedTeamIds().contains(team.getTeamId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.spigot().respawn();
                player.setGameMode(GameMode.SPECTATOR);
            }, 1L);
            match.broadcastMessage("§c" + player.getName() + " est définitivement éliminé !");
        } else {
            Location finalSpawn = spawn;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.spigot().respawn();
                if (finalSpawn != null) {
                    player.teleport(finalSpawn);
                }
            }, 1L);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> checkEndConditions(match), 5L);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        Match match = gameManager.getPlayerMatch(player.getUniqueId());
        if (match == null || match.getCurrentPhase() != GamePhase.DESTRUCTION) {
            return;
        }
        for (NexusCore core : match.getNexusCores().values()) {
            if (!core.isVulnerable()) {
                continue;
            }
            if (core.getLocation() == null || !core.getLocation().getWorld().equals(damaged.getWorld())) {
                continue;
            }
            if (damaged.getLocation().distance(core.getLocation()) > 1) {
                continue;
            }
            Team attackerTeam = match.getTeamOfPlayer(player.getUniqueId());
            if (attackerTeam != null && attackerTeam.getTeamId() == core.getTeam().getTeamId()) {
                event.setCancelled(true);
                return;
            }
            double dmg = computeWeaponDamage(player.getInventory().getItemInMainHand());
            core.damage(dmg);
            event.setCancelled(true);
            if (core.isDestroyed()) {
                match.getPhaseManager().transitionTo(match, GamePhase.ELIMINATION, core.getTeam());
            }
            break;
        }
    }

    private double computeWeaponDamage(ItemStack item) {
        Material type = item.getType();
        return switch (type) {
            case WOODEN_SWORD -> 2;
            case STONE_SWORD -> 3;
            case IRON_SWORD -> 4;
            case DIAMOND_SWORD -> 6;
            case NETHERITE_SWORD -> 8;
            default -> 1;
        };
    }

    private void checkEndConditions(Match match) {
        int aliveTeams = 0;
        int lastTeamId = -1;
        for (Team t : match.getTeams().values()) {
            boolean hasAlive = false;
            for (UUID pid : t.getPlayers()) {
                Player p = Bukkit.getPlayer(pid);
                if (p != null && p.getGameMode() != GameMode.SPECTATOR) {
                    hasAlive = true;
                    break;
                }
            }
            if (hasAlive) {
                aliveTeams++;
                lastTeamId = t.getTeamId();
            }
        }
        if (aliveTeams <= 1 && lastTeamId != -1) {
            gameManager.endRound(match, lastTeamId);
        }
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
