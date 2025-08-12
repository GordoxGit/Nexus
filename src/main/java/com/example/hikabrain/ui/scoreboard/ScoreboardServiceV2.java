package com.example.hikabrain.ui.scoreboard;

import com.example.hikabrain.Arena;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.Team;
import com.example.hikabrain.HikaScoreboard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * New scoreboard service with event driven updates and no flicker.
 */
public class ScoreboardServiceV2 implements ScoreboardService {
    private final HikaBrainPlugin plugin;
    private final Map<UUID, HikaScoreboard> boards = new HashMap<>();
    private final Map<UUID, LobbyBoard> lobbyBoards = new HashMap<>();
    private final String lobbySeparator;

    public ScoreboardServiceV2(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.lobbySeparator = plugin.getConfig().getString("ui.lobby.separator", "§8────────");
        int interval = plugin.getConfig().getInt("ui.lobby.update_interval_ticks", 20);
        new BukkitRunnable(){ @Override public void run(){ updateLobby(); }}.runTaskTimer(plugin, interval, interval);
    }

    /** Render the scoreboard for all players in the arena. */
    public void render(Arena arena) {
        update(arena);
    }

    /** Called once per second to update timer line. */
    public void tick(Arena arena) {
        updateTimer(arena);
    }

    public void updateScore(Arena arena) { update(arena); }
    public void updatePlayers(Arena arena) { update(arena); }
    public void updateTimer(Arena arena) { update(arena); }

    @Override
    public void show(Player p, Arena arena) {
        LobbyBoard lb = lobbyBoards.remove(p.getUniqueId());
        if (lb != null) lb.hide(p);
        HikaScoreboard sb = boards.computeIfAbsent(p.getUniqueId(), k -> new HikaScoreboard(plugin));
        sb.show(p);
        sb.update(arena, p, plugin.game().timeRemaining());
    }

    @Override
    public void hide(Player p) {
        HikaScoreboard sb = boards.remove(p.getUniqueId());
        if (sb != null) sb.hide(p);
        LobbyBoard lb = lobbyBoards.remove(p.getUniqueId());
        if (lb != null) lb.hide(p);
    }

    @Override
    public void update(Arena arena) {
        if (arena == null) return;
        int time = plugin.game().timeRemaining();
        for (UUID u : arena.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                show(p, arena);
                boards.get(u).update(arena, p, time);
            }
        }
        for (UUID u : arena.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                show(p, arena);
                boards.get(u).update(arena, p, time);
            }
        }
        for (UUID u : arena.players().getOrDefault(Team.SPECTATOR, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                show(p, arena);
                boards.get(u).update(arena, p, time);
            }
        }
    }

    @Override
    public void reload() {
        for (Map.Entry<UUID, HikaScoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                e.getValue().rebuild();
                e.getValue().show(p);
            }
        }
        update(plugin.game().arena());
    }

    @Override
    public void showLobby(Player p) {
        HikaScoreboard sb = boards.remove(p.getUniqueId());
        if (sb != null) sb.hide(p);
        LobbyBoard lb = lobbyBoards.computeIfAbsent(p.getUniqueId(), k -> new LobbyBoard());
        lb.show(p);
        lb.update(p);
    }

    @Override
    public void updateLobby() {
        for (Map.Entry<UUID, LobbyBoard> e : new ArrayList<>(lobbyBoards.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) e.getValue().update(p);
        }
    }

    @Override
    public void clear() {
        for (Map.Entry<UUID, HikaScoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) e.getValue().hide(p);
        }
        for (Map.Entry<UUID, LobbyBoard> e : lobbyBoards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) e.getValue().hide(p);
        }
        boards.clear();
        lobbyBoards.clear();
    }

    /** Minimal scoreboard used in lobby. */
    private class LobbyBoard {
        private final Scoreboard board;
        private final Objective obj;
        private final org.bukkit.scoreboard.Team[] lines = new org.bukkit.scoreboard.Team[15];
        private final String[] ENTRIES = {
                "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
                "§8", "§9", "§a", "§b", "§c", "§d", "§e"
        };

        LobbyBoard() {
            ScoreboardManager m = Bukkit.getScoreboardManager();
            board = m.getNewScoreboard();
            String title = ChatColor.AQUA + plugin.serverDisplayName() + ChatColor.DARK_GRAY + " • " + ChatColor.WHITE + "Lobby";
            obj = board.registerNewObjective("hbl", "dummy", title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            for (int i = 0; i < 15; i++) {
                lines[i] = board.getTeam("ll" + i);
                if (lines[i] == null) lines[i] = board.registerNewTeam("ll" + i);
                lines[i].addEntry(ENTRIES[i]);
            }
        }

        void show(Player p) { p.setScoreboard(board); }

        void hide(Player p) { p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); }

        void update(Player p) {
            List<String> built = new ArrayList<>();
            built.add(ChatColor.GRAY + "Monde: " + ChatColor.WHITE + p.getWorld().getName());
            built.add(ChatColor.GRAY + "Joueurs: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size());
            built.add(lobbySeparator);
            built.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + "HikaBrain");
            built.add(ChatColor.GRAY + "Site: " + ChatColor.DARK_GRAY + plugin.serverDomain());
            int n = built.size();
            for (int i = 0; i < n; i++) {
                lines[i].setPrefix(built.get(i));
                obj.getScore(ENTRIES[i]).setScore(n - i);
            }
            for (int i = n; i < 15; i++) {
                lines[i].setPrefix("");
                board.resetScores(ENTRIES[i]);
            }
        }
    }
}
