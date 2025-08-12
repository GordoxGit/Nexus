package com.example.hikabrain.ui.tablist;

import com.example.hikabrain.Arena;
import org.bukkit.entity.Player;

public interface TablistService {
    void update(Arena arena);
    void remove(Player p);
    void reload();
}
