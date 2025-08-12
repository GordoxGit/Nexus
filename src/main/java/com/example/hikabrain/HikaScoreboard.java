package com.example.hikabrain;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

/**
 * Player scoreboard used by ScoreboardService. Lines are pooled
 * and updated via teams to avoid flicker.
 */
public class HikaScoreboard {
    private final HikaBrainPlugin plugin;
    private Scoreboard board;
    private Objective obj;
    private final org.bukkit.scoreboard.Team[] lines = new org.bukkit.scoreboard.Team[15];
    private static final String[] ENTRIES = {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e"
    };

    public HikaScoreboard(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        rebuild();
    }

    /** Recreate the internal scoreboard and objective. */
    public void rebuild() {
        ScoreboardManager m = Bukkit.getScoreboardManager();
        board = m.getNewScoreboard();
        String title = ChatColor.AQUA + plugin.serverDisplayName() + ChatColor.DARK_GRAY + " • " + ChatColor.WHITE + "HikaBrain";
        obj = board.registerNewObjective("hb", "dummy", title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < 15; i++) {
            lines[i] = board.getTeam("l" + i);
            if (lines[i] == null) lines[i] = board.registerNewTeam("l" + i);
            lines[i].addEntry(ENTRIES[i]);
            obj.getScore(ENTRIES[i]).setScore(15 - i);
        }
    }

    public void show(Player p) { p.setScoreboard(board); }

    public void hide(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void update(Arena arena, Player p, int timeRemaining) {
        if (arena == null) return;
        int redAlive = arena.players().get(Team.RED).size();
        int blueAlive = arena.players().get(Team.BLUE).size();
        int players = redAlive + blueAlive;
        String mmss = String.format("%02d:%02d", Math.max(0, timeRemaining) / 60, Math.max(0, timeRemaining) % 60);
        lines[0].setPrefix(ChatColor.GRAY + "Map: " + ChatColor.WHITE + arena.name());
        lines[1].setPrefix(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + "Normal");
        lines[2].setPrefix(ChatColor.GRAY + "Temps: " + ChatColor.WHITE + mmss);
        lines[3].setPrefix(ChatColor.RED + "Rouge: " + ChatColor.WHITE + arena.redScore() +
                ChatColor.BLUE + " Bleu: " + ChatColor.WHITE + arena.blueScore());
        lines[4].setPrefix(ChatColor.RED + "Vivs R: " + ChatColor.WHITE + redAlive +
                ChatColor.BLUE + " Vivs B: " + ChatColor.WHITE + blueAlive);
        lines[5].setPrefix(ChatColor.GRAY + "Série: " + ChatColor.WHITE + "0");
        lines[6].setPrefix(ChatColor.RED + "Lit R: " + ChatColor.GREEN + "✓");
        lines[7].setPrefix(ChatColor.BLUE + "Lit B: " + ChatColor.GREEN + "✓");
        lines[8].setPrefix(ChatColor.GRAY + "Kills: " + ChatColor.WHITE + "0 " +
                ChatColor.GRAY + "Morts: " + ChatColor.WHITE + "0");
        lines[9].setPrefix(ChatColor.GRAY + "Ping: " + ChatColor.WHITE + p.getPing() + "ms");
        lines[10].setPrefix(ChatColor.GRAY + "Joueurs: " + ChatColor.WHITE + players + "/" + 8);
        lines[11].setPrefix(ChatColor.GRAY + "Serveur: " + ChatColor.AQUA + plugin.serverDisplayName());
        lines[12].setPrefix(ChatColor.DARK_GRAY + "─────────────");
        lines[13].setPrefix(ChatColor.GRAY + "/hb help");
        lines[14].setPrefix(ChatColor.DARK_GRAY + plugin.serverDomain());
    }
}
