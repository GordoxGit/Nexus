package com.example.hikabrain;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.List;

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
    private final List<LineType> structure = new ArrayList<>();
    private String cachedSeparator = "";
    private int cachedSepWidth = -1;

    private enum LineType { MAP, MODE, TIME, SCORE, STREAK, PLAYERS, SEPARATOR, HELP, DOMAIN }

    private static class Line {
        final LineType type;
        final String text;
        Line(LineType type, String text) { this.type = type; this.text = text; }
    }

    public HikaScoreboard(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        rebuild();
    }

    /** Recreate the internal scoreboard and objective. */
    public void rebuild() {
        ScoreboardManager m = Bukkit.getScoreboardManager();
        board = m.getNewScoreboard();
        String title = ChatColor.AQUA + plugin.style().brandTitle() + ChatColor.DARK_GRAY + " • " + ChatColor.WHITE + plugin.style().brandSub();
        obj = board.registerNewObjective("hb", "dummy", title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < 15; i++) {
            lines[i] = board.getTeam("l" + i);
            if (lines[i] == null) lines[i] = board.registerNewTeam("l" + i);
            lines[i].addEntry(ENTRIES[i]);
        }
    }

    public void show(Player p) { p.setScoreboard(board); }

    public void hide(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void update(Arena arena, Player p, int timeRemaining) {
        if (arena == null) return;
        List<Line> built = buildLines(arena, timeRemaining);
        boolean needRebuild = structure.size() != built.size();
        if (!needRebuild) {
            for (int i = 0; i < built.size(); i++) {
                if (structure.get(i) != built.get(i).type) { needRebuild = true; break; }
            }
        }
        if (needRebuild) {
            rebuild();
            structure.clear();
            for (Line l : built) structure.add(l.type);
            int n = built.size();
            for (int i = 0; i < n; i++) obj.getScore(ENTRIES[i]).setScore(n - i);
            for (int i = n; i < 15; i++) board.resetScores(ENTRIES[i]);
        }
        int n = built.size();
        for (int i = 0; i < n; i++) {
            lines[i].setPrefix(built.get(i).text);
        }
        for (int i = n; i < 15; i++) {
            lines[i].setPrefix("");
        }
    }

    private List<Line> buildLines(Arena arena, int timeRemaining) {
        List<Line> L = new ArrayList<>();
        if (arena != null && arena.name() != null)
            L.add(new Line(LineType.MAP, ChatColor.GRAY + "Map: " + ChatColor.WHITE + arena.name()));
        int teamSize = plugin.game().teamSize();
        if (teamSize > 0)
            L.add(new Line(LineType.MODE, ChatColor.GRAY + "Mode: " + ChatColor.WHITE + teamSize + "v" + teamSize));
        String mmss = String.format("%02d:%02d", Math.max(0, timeRemaining) / 60, Math.max(0, timeRemaining) % 60);
        L.add(new Line(LineType.TIME, ChatColor.GRAY + "Temps: " + ChatColor.WHITE + mmss));
        L.add(new Line(LineType.SCORE,
                ChatColor.RED + "Rouge: " + ChatColor.WHITE + arena.redScore() + "  " +
                        ChatColor.BLUE + "Bleu: " + ChatColor.WHITE + arena.blueScore()));
        int streak = 0; // placeholder streak
        if (streak > 0)
            L.add(new Line(LineType.STREAK, ChatColor.GRAY + "Série: " + ChatColor.WHITE + streak));
        if (arena != null) {
            int capacity = teamSize * 2;
            int inArena = plugin.game().playersInArena(arena);
            L.add(new Line(LineType.PLAYERS,
                    ChatColor.GRAY + "Joueurs: " + ChatColor.WHITE + inArena + "/" + capacity));
        }
        int max = 12;
        for (Line l : L) max = Math.max(max, visualWidth(l.text));
        L.add(new Line(LineType.SEPARATOR, separator(max)));
        L.add(new Line(LineType.HELP, ChatColor.GRAY + "/hb help"));
        L.add(new Line(LineType.DOMAIN, ChatColor.DARK_GRAY + plugin.style().domain()));
        return L;
    }

    private static int visualWidth(String s) {
        int w = 0; boolean skip = false;
        for (char c : s.toCharArray()) {
            if (skip) { skip = false; continue; }
            if (c == '§') { skip = true; continue; }
            w++;
        }
        return w;
    }

    private String separator(int width) {
        if (width == cachedSepWidth) return cachedSeparator;
        int w = Math.max(1, Math.min(width, 20));
        cachedSepWidth = width;
        cachedSeparator = ChatColor.DARK_GRAY + "" + "\u2500".repeat(w);
        return cachedSeparator;
    }
}
