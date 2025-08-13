package com.example.hikabrain.ui.tablist;

import com.example.hikabrain.Arena;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.Team;
import com.example.hikabrain.ui.style.UiStyle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TablistServiceV2 implements TablistService {
    private final HikaBrainPlugin plugin;
    private final Set<UUID> lobbyPlayers = new HashSet<>();
    private BukkitTask arenaTask;
    private BukkitTask lobbyTask;
    private final AtomicBoolean debugLogged = new AtomicBoolean();
    private final AtomicBoolean warnLogged = new AtomicBoolean();
    private int nullTicks = 0;

    public TablistServiceV2(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        int interval = plugin.style().updateIntervalTicks();
        arenaTask = new BukkitRunnable(){
            @Override public void run(){
                if (!plugin.isEnabled()) return;
                var game = plugin.game();
                Arena a = (game != null) ? game.arena() : null;
                if (a == null) {
                    if (debugLogged.compareAndSet(false, true)) {
                        plugin.getLogger().fine("Tablist update skipped: game or arena not initialized");
                    }
                    nullTicks++;
                    if (nullTicks >= 200 && warnLogged.compareAndSet(false, true)) {
                        plugin.getLogger().warning("TablistService still waiting for game initialization");
                    }
                    return;
                }
                nullTicks = 0;
                update(a);
            }
        }.runTaskTimer(plugin, interval, interval);
        int lobbyInterval = plugin.getConfig().getInt("ui.lobby.update_interval_ticks", 20);
        lobbyTask = new BukkitRunnable(){
            @Override public void run(){ updateLobby(); }
        }.runTaskTimer(plugin, lobbyInterval, lobbyInterval);
    }

    @Override
    public void update(Arena arena) {
        if (arena == null) return;
        var game = plugin.game();
        if (game == null) return;
        UiStyle style = plugin.style();
        int time = game.timeRemaining();
        String mmss = String.format("%02d:%02d", Math.max(0, time) / 60, Math.max(0, time) % 60);
        int teamSize = game.teamSize();
        String mode = teamSize + "v" + teamSize;
        String header = ChatColor.AQUA + "" + ChatColor.BOLD + style.brandTitle().toUpperCase() + "\n" +
                ChatColor.GRAY + style.brandSub() + " " + ChatColor.DARK_GRAY + "• " + ChatColor.GRAY + arena.name() + " " +
                ChatColor.DARK_GRAY + "• " + ChatColor.GRAY + mode;
        String footer = ChatColor.GRAY + "Rouge: " + ChatColor.WHITE + arena.redScore() + "  " +
                ChatColor.GRAY + "Bleu: " + ChatColor.WHITE + arena.blueScore() + "  " + ChatColor.DARK_GRAY + "|  " +
                ChatColor.GRAY + "Temps: " + ChatColor.WHITE + mmss + "\n" +
                ChatColor.DARK_GRAY + style.domain();
        for (UUID u : arena.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.setPlayerListHeaderFooter(header, footer);
                p.setPlayerListName(ChatColor.RED + p.getName());
            }
        }
        for (UUID u : arena.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.setPlayerListHeaderFooter(header, footer);
                p.setPlayerListName(ChatColor.BLUE + p.getName());
            }
        }
        for (UUID u : arena.players().getOrDefault(Team.SPECTATOR, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.setPlayerListHeaderFooter(header, footer);
                p.setPlayerListName(ChatColor.GRAY + "[SPEC] " + ChatColor.WHITE + p.getName());
            }
        }
    }

    @Override
    public void remove(Player p) {
        if (p != null) {
            p.setPlayerListName(p.getName());
            p.setPlayerListHeaderFooter("", "");
            lobbyPlayers.remove(p.getUniqueId());
        }
    }

    @Override
    public void reload() {
        var game = plugin.game();
        if (game != null) update(game.arena());
        updateLobby();
    }

    @Override
    public void clear() {
        if (arenaTask != null) arenaTask.cancel();
        if (lobbyTask != null) lobbyTask.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            remove(p);
        }
    }

    @Override
    public void showLobby(Player p) {
        lobbyPlayers.add(p.getUniqueId());
        updateLobbyPlayer(p);
    }

    private void updateLobby() {
        for (UUID u : new HashSet<>(lobbyPlayers)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) updateLobbyPlayer(p);
        }
    }

    private void updateLobbyPlayer(Player p) {
        String header = ChatColor.AQUA + "" + ChatColor.BOLD + "HENERIA\n" + ChatColor.GRAY + "Lobby " + ChatColor.DARK_GRAY + "• " + ChatColor.GRAY + "HikaBrain";
        String footer = ChatColor.GRAY + "Connectés: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size() + "  " + ChatColor.DARK_GRAY + "|  " + ChatColor.GRAY + "Monde: " + ChatColor.WHITE + p.getWorld().getName() + "\n" + ChatColor.DARK_GRAY + plugin.serverDomain();
        p.setPlayerListHeaderFooter(header, footer);
        p.setPlayerListName(p.getName());
    }
}
