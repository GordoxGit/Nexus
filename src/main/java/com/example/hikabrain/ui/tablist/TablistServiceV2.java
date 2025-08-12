package com.example.hikabrain.ui.tablist;

import com.example.hikabrain.Arena;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.Team;
import com.example.hikabrain.ui.style.UiStyle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TablistServiceV2 implements TablistService {
    private final HikaBrainPlugin plugin;
    private final Set<UUID> lobbyPlayers = new HashSet<>();

    public TablistServiceV2(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        int interval = plugin.style().updateIntervalTicks();
        new BukkitRunnable(){
            @Override public void run(){
                Arena a = plugin.game().arena();
                if (a != null) update(a);
            }
        }.runTaskTimer(plugin, interval, interval);
        int lobbyInterval = plugin.getConfig().getInt("ui.lobby.update_interval_ticks", 20);
        new BukkitRunnable(){
            @Override public void run(){ updateLobby(); }
        }.runTaskTimer(plugin, lobbyInterval, lobbyInterval);
    }

    @Override
    public void update(Arena arena) {
        if (arena == null) return;
        UiStyle style = plugin.style();
        int time = plugin.game().timeRemaining();
        String mmss = String.format("%02d:%02d", Math.max(0, time) / 60, Math.max(0, time) % 60);
        int teamSize = plugin.game().teamSize();
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
        update(plugin.game().arena());
        updateLobby();
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
