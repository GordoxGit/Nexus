package com.example.hikabrain.ui;

import com.example.hikabrain.Arena;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.ui.model.Palette;
import com.example.hikabrain.ui.model.Theme;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ThemeServiceImpl implements ThemeService {
    private final HikaBrainPlugin plugin;
    private final Map<String, Theme> themes = new HashMap<>();
    private final Map<Arena, Theme> applied = new HashMap<>();
    private String defaultId = "classic";

    public ThemeServiceImpl(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        themes.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("themes");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection tsec = sec.getConfigurationSection(id);
                if (tsec == null) continue;
                Palette red = parsePalette(tsec.getConfigurationSection("red"));
                Palette blue = parsePalette(tsec.getConfigurationSection("blue"));
                String bank = tsec.getString("soundBank", "");
                themes.put(id, new Theme(id, red, blue, bank));
            }
        }
        defaultId = plugin.getConfig().getString("ui.theme", "classic");
    }

    private Palette parsePalette(ConfigurationSection sec) {
        if (sec == null) return new Palette(0xFFFFFF, 0x000000);
        int pri = parseColor(sec.getString("primary", "#ffffff"));
        int secCol = parseColor(sec.getString("secondary", "#000000"));
        return new Palette(pri, secCol);
    }

    private int parseColor(String s) {
        if (s == null) return 0xFFFFFF;
        if (s.startsWith("#")) s = s.substring(1);
        try { return Integer.parseInt(s, 16); } catch (NumberFormatException e) { return 0xFFFFFF; }
    }

    @Override
    public void applyTheme(Arena a, String themeId) {
        Theme t = themes.getOrDefault(themeId, themes.get(defaultId));
        if (t == null) t = themes.values().stream().findFirst().orElse(null);
        if (t != null) {
            applied.put(a, t);
            String path = "arenas." + a.name() + ".ui.theme";
            if (plugin.getConfig().isConfigurationSection("arenas." + a.name())) {
                plugin.getConfig().set(path, themeId);
                plugin.saveConfig();
            }
        }
    }

    @Override
    public Theme themeOf(Arena a) {
        Theme t = applied.get(a);
        if (t == null) {
            t = themes.getOrDefault(defaultId, themes.values().stream().findFirst().orElse(null));
            if (t != null) applied.put(a, t);
        }
        return t;
    }

    @Override
    public Set<String> available() {
        return new HashSet<>(themes.keySet());
    }
}
