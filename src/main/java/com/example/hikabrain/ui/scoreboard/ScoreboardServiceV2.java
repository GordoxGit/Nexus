package com.example.hikabrain.ui.scoreboard;

import com.example.hikabrain.Arena;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.Team;
import com.example.hikabrain.HikaScoreboard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * New scoreboard service with event driven updates and no flicker.
 */
public class ScoreboardServiceV2 implements ScoreboardService {
    private final HikaBrainPlugin plugin;
    private final Map<UUID, HikaScoreboard> boards = new HashMap<>();

    public ScoreboardServiceV2(HikaBrainPlugin plugin) {
        this.plugin = plugin;
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
        HikaScoreboard sb = boards.computeIfAbsent(p.getUniqueId(), k -> new HikaScoreboard(plugin));
        sb.show(p);
        sb.update(arena, p, plugin.game().timeRemaining());
    }

    @Override
    public void hide(Player p) {
        HikaScoreboard sb = boards.remove(p.getUniqueId());
        if (sb != null) sb.hide(p);
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
    public void clear() {
        for (Map.Entry<UUID, HikaScoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) e.getValue().hide(p);
        }
        boards.clear();
    }
}
