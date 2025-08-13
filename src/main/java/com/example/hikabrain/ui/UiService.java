package com.example.hikabrain.ui;

import com.example.hikabrain.Arena;
import com.example.hikabrain.GamePhase;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.Sound;

public interface UiService {
    void pushActionbar(Player p, Component c, int ttlTicks);
    void setBossPhase(GamePhase phase, float progress);
    void showIntroCountdown(Arena a, int seconds);
    void broadcastTitle(Arena a, String title, String subtitle, int fadeIn, int stay, int fadeOut);
    void broadcastSound(Arena a, Sound sound, float volume, float pitch);
    void updateSidebar(Arena a);
    void clearAll(Arena a);
}
