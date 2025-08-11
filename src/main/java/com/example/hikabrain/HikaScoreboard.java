package com.example.hikabrain;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashSet;
import java.util.Set;

public class HikaScoreboard {
    private final Scoreboard board;
    private final Objective obj;
    private final Set<Player> shown = new HashSet<>();
    private int red=0, blue=0, time=0;

    public HikaScoreboard() {
        ScoreboardManager m = Bukkit.getScoreboardManager();
        board = m.getNewScoreboard();
        obj = board.registerNewObjective("hikabrain","dummy", ChatColor.GOLD + "HikaBrain");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        refresh();
    }

    public void setScores(int r, int b) { red=r; blue=b; refresh(); }
    public void setTime(int seconds) { time=seconds; refresh(); }

    private void refresh() {
        for (String e : new java.util.HashSet<>(board.getEntries())) board.resetScores(e);
        obj.getScore(ChatColor.RED + "Rouge: " + red).setScore(3);
        obj.getScore(ChatColor.BLUE + "Bleu: " + blue).setScore(2);
        int m = time / 60, s = time % 60;
        obj.getScore(ChatColor.YELLOW + String.format("Temps: %02d:%02d", m, s)).setScore(1);
    }

    public void show(Player p) { shown.add(p); p.setScoreboard(board); }
    public void hide(Player p){ shown.remove(p); p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); }
    public void hideAll(){ for (Player p: new HashSet<>(shown)) hide(p); }
}
