package com.example.hikabrain.ui;

import com.example.hikabrain.Arena;
import com.example.hikabrain.GamePhase;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface UiService {
    void pushActionbar(Player p, Component c, int ttlTicks);
    void setBossPhase(GamePhase phase, float progress);
    void showIntroCountdown(Arena a, int seconds);
    void updateSidebar(Arena a);
    void clearAll(Arena a);
}
