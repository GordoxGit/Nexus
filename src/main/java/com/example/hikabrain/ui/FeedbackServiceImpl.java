package com.example.hikabrain.ui;

import com.example.hikabrain.Arena;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.Team;
import com.example.hikabrain.ui.model.Preset;
import com.example.hikabrain.ui.model.Presets;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FeedbackServiceImpl implements FeedbackService {
    private final HikaBrainPlugin plugin;
    private int throttleTicks = 5;
    private final Map<UUID, Long> lastPlay = new HashMap<>();
    private final Map<String, Preset> presets = new HashMap<>();
    private final Map<String, Particle.DustOptions> dustCache = new HashMap<>();

    public FeedbackServiceImpl(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        throttleTicks = plugin.getConfig().getInt("feedback.throttle_ticks", 5);
        presets.clear();
        dustCache.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("feedback.presets");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                String sound = sec.getString(id + ".sound", "");
                String particleStr = sec.getString(id + ".particle", "");
                Preset p = parsePreset(id, sound, particleStr);
                presets.put(id, p);
            }
        }
        Presets.HIT_SOFT = presets.get("hit_soft");
        Presets.SCORE_BED = presets.get("score_bed");
        Presets.CLUTCH_SAVE = presets.get("clutch_save");
    }

    private Preset parsePreset(String id, String soundStr, String particleStr) {
        String[] sParts = soundStr.split(":");
        String soundName = sParts.length>0? sParts[0] : "";
        float vol = sParts.length>1? parseFloat(sParts[1],1f):1f;
        float pitch = sParts.length>2? parseFloat(sParts[2],1f):1f;
        Particle particle = null;
        int count = 0;
        if (particleStr != null && !particleStr.isEmpty()) {
            String[] pParts = particleStr.split(":");
            try {
                particle = Particle.valueOf(pParts[0]);
                count = pParts.length>2? Integer.parseInt(pParts[2]) : (pParts.length>1? Integer.parseInt(pParts[1]):1);
                if (particle == Particle.DUST && pParts.length>1) {
                    String colorHex = pParts[1];
                    if (colorHex.startsWith("#")) colorHex = colorHex.substring(1);
                    int rgb = Integer.parseInt(colorHex, 16);
                    Color color = Color.fromRGB(rgb);
                    dustCache.put(id, new Particle.DustOptions(color,1f));
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return new Preset(id, soundName, vol, pitch, particle, count);
    }

    private float parseFloat(String s, float def){
        try { return Float.parseFloat(s); } catch (NumberFormatException e){ return def; }
    }

    private boolean throttled(Player p) {
        long now = System.currentTimeMillis();
        long last = lastPlay.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < throttleTicks * 50L) return true;
        lastPlay.put(p.getUniqueId(), now);
        return false;
    }

    @Override
    public void playPreset(Player p, Preset preset) {
        if (p == null || preset == null) return;
        if (throttled(p)) return;
        if (preset.sound() != null && !preset.sound().isEmpty()) {
            try {
                Sound s = Sound.valueOf(preset.sound());
                p.playSound(p.getLocation(), s, preset.vol(), preset.pitch());
            } catch (IllegalArgumentException ignored) {}
        }
        if (preset.particle() != null) {
            if (preset.particle() == Particle.DUST) {
                Particle.DustOptions opt = dustCache.get(preset.id());
                if (opt == null) opt = new Particle.DustOptions(Color.WHITE, 1f);
                p.spawnParticle(preset.particle(), p.getLocation(), preset.count(), 0,0,0,0, opt);
            } else {
                p.spawnParticle(preset.particle(), p.getLocation(), preset.count());
            }
        }
    }

    @Override
    public void playTeamPreset(Team team, Preset preset) {
        Arena a = plugin.game().arena();
        if (a == null) return;
        Set<UUID> set = a.players().getOrDefault(team, new HashSet<>());
        for (UUID u : set) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) playPreset(p, preset);
        }
    }

    @Override
    public void playArena(Arena a, Preset preset) {
        if (a == null) return;
        for (UUID u : a.players().getOrDefault(Team.RED, new HashSet<>())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) playPreset(p, preset);
        }
        for (UUID u : a.players().getOrDefault(Team.BLUE, new HashSet<>())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) playPreset(p, preset);
        }
    }
}
