package com.example.hikabrain.ui;

import com.example.hikabrain.Arena;
import com.example.hikabrain.Team;
import com.example.hikabrain.ui.model.Preset;
import org.bukkit.entity.Player;

public interface FeedbackService {
    void playPreset(Player p, Preset preset);
    void playTeamPreset(Team team, Preset preset);
    void playArena(Arena a, Preset preset);
}
