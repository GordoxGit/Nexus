package fr.heneria.nexus.game.manager;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.economy.manager.EconomyManager;
import fr.heneria.nexus.game.kit.manager.KitManager;
import fr.heneria.nexus.game.kit.model.Kit;
import fr.heneria.nexus.game.model.GameState;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import fr.heneria.nexus.game.repository.MatchRepository;
import fr.heneria.nexus.game.phase.GamePhase;
import fr.heneria.nexus.game.phase.PhaseManager;
import fr.heneria.nexus.gui.player.ShopGui;
import fr.heneria.nexus.shop.manager.ShopManager;
import fr.heneria.nexus.player.manager.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {

    private static GameManager instance;

    private final JavaPlugin plugin;
    private final ArenaManager arenaManager;
    private final PlayerManager playerManager;
    private final MatchRepository matchRepository;
    private final KitManager kitManager;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final Map<UUID, Match> matches = new ConcurrentHashMap<>();
    private final Map<UUID, Match> playerMatches = new ConcurrentHashMap<>();

    private GameManager(JavaPlugin plugin, ArenaManager arenaManager, PlayerManager playerManager, MatchRepository matchRepository,
                        KitManager kitManager, ShopManager shopManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.playerManager = playerManager;
        this.matchRepository = matchRepository;
        this.kitManager = kitManager;
        this.shopManager = shopManager;
        this.economyManager = economyManager;
    }

    public static void init(JavaPlugin plugin, ArenaManager arenaManager, PlayerManager playerManager, MatchRepository matchRepository,
                            KitManager kitManager, ShopManager shopManager, EconomyManager economyManager) {
        instance = new GameManager(plugin, arenaManager, playerManager, matchRepository, kitManager, shopManager, economyManager);
    }

    public static GameManager getInstance() {
        return instance;
    }

    public Match createMatch(Arena arena, List<List<UUID>> playerTeams) {
        UUID matchId = UUID.randomUUID();
        Match match = new Match(matchId, arena);
        match.setPhaseManager(new PhaseManager(plugin));
        for (int i = 0; i < playerTeams.size(); i++) {
            Team team = new Team(i + 1);
            for (UUID uuid : playerTeams.get(i)) {
                team.addPlayer(uuid);
                playerMatches.put(uuid, match);
            }
            match.addTeam(team);
        }
        matches.put(matchId, match);
        return match;
    }

    public void startMatchCountdown(Match match) {
        match.setState(GameState.STARTING);
        BukkitRunnable runnable = new BukkitRunnable() {
            int counter = 10;
            @Override
            public void run() {
                if (counter <= 0) {
                    cancel();
                    startMatch(match);
                    return;
                }
                match.broadcastMessage("La partie commence dans " + counter + " seconde(s)");
                counter--;
            }
        };
        match.setCountdownTask(runnable.runTaskTimer(plugin, 0L, 20L));
    }

    public void startMatch(Match match) {
        match.setState(GameState.IN_PROGRESS);
        match.setStartTime(Instant.now());

        match.initNexusCores();

        boolean teamMode = match.getTeams().values().stream().anyMatch(t -> t.getPlayers().size() > 1);
        Kit kit = kitManager.getKit(teamMode ? "Equipe" : "Solo");

        for (Team team : match.getTeams().values()) {
            Map<Integer, Location> spawns = match.getArena().getSpawns().get(team.getTeamId());
            Location spawn = null;
            if (spawns != null && !spawns.isEmpty()) {
                spawn = spawns.values().iterator().next();
            }
            for (UUID playerId : team.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    if (spawn != null) {
                        player.teleport(spawn);
                    }
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setHealth(player.getMaxHealth());
                    player.setFoodLevel(20);
                    kitManager.applyKit(player, kit);
                    new ShopGui(shopManager, economyManager, playerManager, plugin).open(player);
                }
            }
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID playerId : match.getPlayers()) {
                Player p = Bukkit.getPlayer(playerId);
                if (p != null) {
                    p.closeInventory();
                    p.sendMessage("La phase d'achat est terminée !");
                }
            }
            if (match.getPhaseManager() != null) {
                match.getPhaseManager().transitionTo(match, GamePhase.CAPTURE);
            }
        }, 400L);
        match.setShopPhaseTask(task);
    }

    public void endMatch(Match match, int winningTeamId) {
        match.setState(GameState.ENDING);
        match.setEndTime(Instant.now());
        matchRepository.saveMatchResult(match, winningTeamId);
        matchRepository.saveMatchParticipants(match);
        Location lobby = plugin.getServer().getWorlds().get(0).getSpawnLocation();
        for (UUID playerId : match.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.getInventory().clear();
                if (lobby != null) {
                    player.teleport(lobby);
                }
            }
            playerMatches.remove(playerId);
        }
        matches.remove(match.getMatchId());
    }

    public Match getPlayerMatch(UUID playerId) {
        return playerMatches.get(playerId);
    }

    public void removePlayer(UUID playerId) {
        Match match = playerMatches.remove(playerId);
        if (match != null) {
            Team team = match.getTeamOfPlayer(playerId);
            if (team != null) {
                team.removePlayer(playerId);
            }
        }
    }

    /**
     * Vérifie si une arène est actuellement utilisée par une partie en cours.
     *
     * @param arena arène à vérifier
     * @return {@code true} si l'arène est occupée, sinon {@code false}
     */
    public boolean isArenaInUse(Arena arena) {
        return matches.values().stream().anyMatch(match -> match.getArena().equals(arena));
    }
}
