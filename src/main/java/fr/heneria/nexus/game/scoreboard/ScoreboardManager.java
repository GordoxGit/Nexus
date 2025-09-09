package fr.heneria.nexus.game.scoreboard;

import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import fr.heneria.nexus.game.model.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère l'affichage du scoreboard pour les parties.
 */
public class ScoreboardManager {

    private static final ScoreboardManager INSTANCE = new ScoreboardManager();
    private final Map<UUID, Scoreboard> scoreboards = new ConcurrentHashMap<>();

    private ScoreboardManager() {
    }

    public static ScoreboardManager getInstance() {
        return INSTANCE;
    }

    /**
     * Crée le scoreboard d'une partie pour tous les joueurs.
     *
     * @param match partie
     */
    public void createMatchScoreboard(Match match) {
        for (UUID playerId : match.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("nexus", "dummy", "§e§lHeneria Nexus");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            scoreboards.put(playerId, board);
            player.setScoreboard(board);
        }
        updateScoreboard(match);
    }

    /**
     * Met à jour le contenu du scoreboard pour tous les joueurs.
     *
     * @param match partie
     */
    public void updateScoreboard(Match match) {
        for (UUID playerId : match.getPlayers()) {
            updatePlayer(match, playerId);
        }
    }

    public void updatePlayer(Match match, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        Scoreboard board = scoreboards.computeIfAbsent(playerId, id -> {
            Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = b.registerNewObjective("nexus", "dummy", "§e§lHeneria Nexus");
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            player.setScoreboard(b);
            return b;
        });
        Objective obj = board.getObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            obj = board.registerNewObjective("nexus", "dummy", "§e§lHeneria Nexus");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
        int line = 15;
        obj.getScore("Manche: §a" + match.getCurrentRound() + "/5").setScore(line--);
        obj.getScore(" ").setScore(line--);
        for (Team team : match.getTeams().values()) {
            int teamScore = match.getTeamScores().getOrDefault(team.getTeamId(), 0);
            String name = TeamColor.coloredName(team.getTeamId());
            obj.getScore(name + ": §f" + teamScore).setScore(line--);
        }
        obj.getScore("  ").setScore(line--);
        int points = match.getRoundPoints().getOrDefault(playerId, 0);
        obj.getScore("Points: §6" + points).setScore(line);
    }

    /**
     * Supprime le scoreboard d'un joueur.
     *
     * @param player joueur
     */
    public void removeScoreboard(Player player) {
        if (player == null) {
            return;
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        scoreboards.remove(player.getUniqueId());
    }
}
