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
import fr.heneria.nexus.game.scoreboard.ScoreboardManager;
import fr.heneria.nexus.economy.model.TransactionType;
import fr.heneria.nexus.player.manager.PlayerManager;
import fr.heneria.nexus.player.model.PlayerProfile;
import fr.heneria.nexus.player.rank.PlayerRank;
import fr.heneria.nexus.ranking.EloCalculator;
import fr.heneria.nexus.ranking.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.HashMap;
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
                    match.getRoundPoints().put(playerId, 100);
                    new ShopGui(shopManager, playerManager, plugin, match).open(player);
                }
            }
        }

        ScoreboardManager.getInstance().createMatchScoreboard(match);

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

    public void endRound(Match match, int winningTeamId) {
        match.setState(GameState.ROUND_ENDING);
        match.getTeamScores().merge(winningTeamId, 1, Integer::sum);
        ScoreboardManager.getInstance().updateScoreboard(match);
        Team winningTeam = match.getTeams().get(winningTeamId);
        String title = "Manche remportée";
        String subtitle = winningTeam != null ? "Equipe " + winningTeamId + " gagne la manche" : "";
        for (UUID playerId : match.getPlayers()) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                p.sendTitle(title, subtitle, 10, 40, 10);
            }
        }
        if (match.getTeamScores().getOrDefault(winningTeamId, 0) >= Match.ROUNDS_TO_WIN) {
            endGame(match, winningTeamId);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> startNextRound(match), 200L);
    }

    public void startNextRound(Match match) {
        match.setState(GameState.IN_PROGRESS);
        match.setCurrentRound(match.getCurrentRound() + 1);
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
                    player.setHealth(player.getMaxHealth());
                    player.setFoodLevel(20);
                    player.getInventory().clear();
                    kitManager.applyKit(player, kit);
                    if (spawn != null) {
                        player.teleport(spawn);
                    }
                    match.getRoundPoints().put(playerId, 100);
                    new ShopGui(shopManager, playerManager, plugin, match).open(player);
                }
            }
        }
        ScoreboardManager.getInstance().updateScoreboard(match);
        if (match.getPhaseManager() != null) {
            match.getPhaseManager().transitionTo(match, GamePhase.PREPARATION);
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

    public void endGame(Match match, int winningTeamId) {
        match.setState(GameState.ENDING);
        match.setEndTime(Instant.now());
        matchRepository.saveMatchResult(match, winningTeamId);
        matchRepository.saveMatchParticipants(match);
        Team winningTeam = match.getTeams().get(winningTeamId);
        if (winningTeam != null) {
            for (UUID uuid : winningTeam.getPlayers()) {
                economyManager.addPoints(uuid, 100, TransactionType.EARN_VICTORY, "match_win");
            }
        }
        for (UUID playerId : match.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendTitle("§6Victoire !", "Equipe " + winningTeamId + " remporte la partie", 10, 60, 10);
            }
        }
        Map<UUID, String> summaries = new HashMap<>();
        Map<Integer, Double> teamAverageElo = new HashMap<>();
        for (Team team : match.getTeams().values()) {
            int sum = 0;
            int count = 0;
            for (UUID uuid : team.getPlayers()) {
                PlayerProfile profile = playerManager.getPlayerProfile(uuid);
                if (profile != null) {
                    sum += profile.getEloRating();
                    count++;
                }
            }
            double avg = count > 0 ? (double) sum / count : 0;
            teamAverageElo.put(team.getTeamId(), avg);
        }

        EloCalculator eloCalculator = new EloCalculator();
        RankManager rankManager = RankManager.getInstance();
        int kFactor = plugin.getConfig().getInt("ranking.elo.k-factor", 32);

        for (Team team : match.getTeams().values()) {
            for (UUID uuid : team.getPlayers()) {
                PlayerProfile profile = playerManager.getPlayerProfile(uuid);
                if (profile == null) {
                    continue;
                }
                double opponentAvg = teamAverageElo.entrySet().stream()
                        .filter(e -> e.getKey() != team.getTeamId())
                        .mapToDouble(Map.Entry::getValue)
                        .average()
                        .orElse(teamAverageElo.getOrDefault(team.getTeamId(), 0.0));
                int opponentAverageElo = (int) Math.round(opponentAvg);
                boolean playerWon = team.getTeamId() == winningTeamId;
                int eloChange = eloCalculator.calculateEloChange(profile.getEloRating(), opponentAverageElo, playerWon, kFactor);
                int oldElo = profile.getEloRating();
                PlayerRank oldRank = profile.getRank();
                int newElo = oldElo + eloChange;
                profile.setEloRating(newElo);
                PlayerRank newRank = rankManager.getRankFromElo(newElo);
                profile.setRank(newRank);
                playerManager.getPlayerRepository().saveProfile(profile);

                StringBuilder sb = new StringBuilder();
                sb.append("§m---------------------------------------------\n");
                sb.append("§e§lRÉSUMÉ DE LA PARTIE\n\n");
                sb.append(playerWon ? "§aVictoire !\n\n" : "§cDéfaite...\n\n");
                sb.append("Classement Elo : §f").append(oldElo).append(" §a(")
                        .append(eloChange >= 0 ? "+" : "").append(eloChange)
                        .append(") §7-> §f").append(newElo).append("\n");
                if (newRank.ordinal() > oldRank.ordinal()) {
                    sb.append("\n§b§lPROMOTION ! Vous êtes maintenant rang ").append(newRank).append(" !\n");
                } else if (newRank.ordinal() < oldRank.ordinal()) {
                    sb.append("\n§c§lRELÉGATION ! Vous êtes maintenant rang ").append(newRank).append(".\n");
                }
                sb.append("§m---------------------------------------------");
                summaries.put(uuid, sb.toString());
            }
        }

        for (Map.Entry<UUID, String> entry : summaries.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.sendMessage(entry.getValue());
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location lobby = plugin.getServer().getWorlds().get(0).getSpawnLocation();
            for (UUID playerId : match.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.getInventory().clear();
                    if (lobby != null) {
                        player.teleport(lobby);
                    }
                    ScoreboardManager.getInstance().removeScoreboard(player);
                    player.sendMessage("Retour au lobby...");
                }
                playerMatches.remove(playerId);
            }
            matches.remove(match.getMatchId());
        }, 100L);
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
