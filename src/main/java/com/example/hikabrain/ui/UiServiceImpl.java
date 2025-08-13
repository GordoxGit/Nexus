package com.example.hikabrain.ui;

import com.example.hikabrain.Arena;
import com.example.hikabrain.GamePhase;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.Team;
import com.example.hikabrain.ui.model.Presets;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import net.kyori.adventure.text.Component;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class UiServiceImpl implements UiService {
    private final HikaBrainPlugin plugin;
    private final ThemeService theme;
    private final FeedbackService fx;

    private final Map<Arena, BossBar> bossbars = new HashMap<>();
    private final Map<UUID, Deque<ActionbarEntry>> actionbars = new HashMap<>();
    private final Map<Arena, Scoreboard> boards = new HashMap<>();

    private boolean bossbarEnabled;
    private boolean actionbarEnabled;
    private boolean scoreboardEnabled;

    public UiServiceImpl(HikaBrainPlugin plugin, ThemeService theme, FeedbackService fx) {
        this.plugin = plugin;
        this.theme = theme;
        this.fx = fx;
        reload();
        new BukkitRunnable(){
            @Override public void run(){ tickActionbars(); }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    public void reload() {
        actionbarEnabled = plugin.getConfig().getBoolean("ui.actionbar", true);
        bossbarEnabled = plugin.getConfig().getBoolean("ui.bossbar", false);
        scoreboardEnabled = plugin.getConfig().getBoolean("ui.scoreboard", true);
    }

    private void tickActionbars() {
        if (!actionbarEnabled) {
            for (UUID id : actionbars.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            }
            actionbars.clear();
            return;
        }
        Iterator<Map.Entry<UUID, Deque<ActionbarEntry>>> it = actionbars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Deque<ActionbarEntry>> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { it.remove(); continue; }
            Deque<ActionbarEntry> q = e.getValue();
            if (q.isEmpty()) { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("")); continue; }
            ActionbarEntry head = q.peek();
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(head.component.toString()));
            head.ttl--;
            if (head.ttl <= 0) q.poll();
        }
    }

    private static class ActionbarEntry {
        final Component component;
        int ttl;
        ActionbarEntry(Component c, int t){ this.component = c; this.ttl = Math.max(1, t); }
    }

    @Override
    public void pushActionbar(Player p, Component c, int ttlTicks) {
        if (!actionbarEnabled) return;
        actionbars.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>()).add(new ActionbarEntry(c, ttlTicks));
    }

    @Override
    public void setBossPhase(GamePhase phase, float progress) {
        Arena a = plugin.game().arena();
        if (a == null) return;
        if (!bossbarEnabled) {
            BossBar bb = bossbars.remove(a);
            if (bb != null) bb.removeAll();
            return;
        }
        BossBar bar = bossbars.computeIfAbsent(a, k -> Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID));
        if (phase == null) {
            bar.removeAll();
            return;
        }
        bar.setProgress(Math.max(0f, Math.min(1f, progress)));
        bar.setTitle(ChatColor.YELLOW + phase.name());
        for (UUID u : a.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u); if (p != null) bar.addPlayer(p);
        }
        for (UUID u : a.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u); if (p != null) bar.addPlayer(p);
        }
    }

    @Override
    public void showIntroCountdown(Arena a, int seconds) {
        new BukkitRunnable(){
            int c = seconds;
            @Override public void run(){
                if (a == null) { cancel(); return; }
                if (c <= 0) { cancel(); return; }
                for (UUID u : a.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
                    Player p = Bukkit.getPlayer(u); if (p != null) p.sendTitle(String.valueOf(c), "", 0, 20, 10);
                }
                for (UUID u : a.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
                    Player p = Bukkit.getPlayer(u); if (p != null) p.sendTitle(String.valueOf(c), "", 0, 20, 10);
                }
                fx.playArena(a, Presets.HIT_SOFT);
                c--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public void broadcastTitle(Arena a, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (a == null) return;
        for (UUID u : a.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
        for (UUID u : a.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
        for (UUID u : a.players().getOrDefault(Team.SPECTATOR, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    @Override
    public void broadcastSound(Arena a, Sound sound, float volume, float pitch) {
        if (a == null) return;
        for (UUID u : a.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
        for (UUID u : a.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
        for (UUID u : a.players().getOrDefault(Team.SPECTATOR, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    @Override
    public void updateSidebar(Arena a) {
        if (a == null) return;
        if (!scoreboardEnabled) {
            Scoreboard sb = boards.remove(a);
            if (sb != null) {
                Scoreboard empty = Bukkit.getScoreboardManager().getNewScoreboard();
                for (UUID u : a.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
                    Player p = Bukkit.getPlayer(u); if (p != null) p.setScoreboard(empty);
                }
                for (UUID u : a.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
                    Player p = Bukkit.getPlayer(u); if (p != null) p.setScoreboard(empty);
                }
            }
            return;
        }
        Scoreboard sb = boards.computeIfAbsent(a, k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective obj = sb.getObjective("hb");
        if (obj == null) {
            obj = sb.registerNewObjective("hb", "dummy", ChatColor.GOLD + "HikaBrain");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        for (String e : sb.getEntries()) sb.resetScores(e);
        obj.getScore(ChatColor.RED + "Rouge: " + a.redScore()).setScore(2);
        obj.getScore(ChatColor.BLUE + "Bleu: " + a.blueScore()).setScore(1);
        for (UUID u : a.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u); if (p != null) p.setScoreboard(sb);
        }
        for (UUID u : a.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u); if (p != null) p.setScoreboard(sb);
        }
    }

    @Override
    public void clearAll(Arena a) {
        BossBar bar = bossbars.remove(a);
        if (bar != null) bar.removeAll();
        Scoreboard sb = boards.remove(a);
        if (sb != null) {
            Scoreboard empty = Bukkit.getScoreboardManager().getNewScoreboard();
            for (UUID u : a.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
                Player p = Bukkit.getPlayer(u); if (p != null) p.setScoreboard(empty);
            }
            for (UUID u : a.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
                Player p = Bukkit.getPlayer(u); if (p != null) p.setScoreboard(empty);
            }
        }
        for (UUID u : a.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) actionbars.remove(u);
        for (UUID u : a.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) actionbars.remove(u);
    }
}
