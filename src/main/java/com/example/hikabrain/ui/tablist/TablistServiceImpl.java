package com.example.hikabrain.ui.tablist;

import com.example.hikabrain.Arena;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.Collections;

public class TablistServiceImpl implements TablistService {
    private final HikaBrainPlugin plugin;

    public TablistServiceImpl(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        new BukkitRunnable(){
            @Override public void run(){
                Arena a = plugin.game().arena();
                if (a != null) update(a);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void update(Arena arena) {
        if (arena == null) return;
        int time = plugin.game().timeRemaining();
        String mmss = String.format("%02d:%02d", Math.max(0, time) / 60, Math.max(0, time) % 60);
        String header = ChatColor.AQUA + "" + ChatColor.BOLD + plugin.serverDisplayName().toUpperCase() + "\n" +
                ChatColor.GRAY + "HikaBrain " + ChatColor.DARK_GRAY + "• " + ChatColor.GRAY + arena.name() +
                ChatColor.DARK_GRAY + " • " + ChatColor.GRAY + "Normal";
        String footer = ChatColor.GRAY + "Rouge: " + ChatColor.WHITE + arena.redScore() + "  " +
                ChatColor.GRAY + "Bleu: " + ChatColor.WHITE + arena.blueScore() + "  " + ChatColor.DARK_GRAY + "|  " +
                ChatColor.GRAY + "Temps: " + ChatColor.WHITE + mmss + "\n" +
                ChatColor.DARK_GRAY + plugin.serverDomain();
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
                p.setPlayerListName(ChatColor.GRAY + "[SPEC] " + p.getName());
            }
        }
    }

    @Override
    public void remove(Player p) {
        if (p != null) {
            p.setPlayerListName(p.getName());
            p.setPlayerListHeaderFooter("", "");
        }
    }

    @Override
    public void reload() {
        update(plugin.game().arena());
    }
}
