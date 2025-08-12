package com.example.hikabrain.ui.scoreboard;

import com.example.hikabrain.Arena;
import org.bukkit.entity.Player;

public interface ScoreboardService {
    void show(Player p, Arena arena);
    void hide(Player p);
    void update(Arena arena);
    void reload();
    void clear();
}
