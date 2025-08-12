package com.example.hikabrain.ui.model;

import org.bukkit.Particle;

public record Preset(String id, String sound, float vol, float pitch,
                     Particle particle, int count) {}
