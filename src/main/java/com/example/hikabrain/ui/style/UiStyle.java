package com.example.hikabrain.ui.style;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Simple holder for UI style configuration.
 */
public class UiStyle {
    private final String brandTitle;
    private final String brandSub;
    private final String domain;
    private final boolean gradientTitle;
    private final String separator;
    private final int updateIntervalTicks;

    public UiStyle(ConfigurationSection sec) {
        this.brandTitle = sec.getString("brand_title", "Heneria");
        this.brandSub = sec.getString("brand_sub", "HikaBrain");
        this.domain = sec.getString("domain", "heneria.com");
        this.gradientTitle = sec.getBoolean("gradient_title", false);
        this.separator = sec.getString("separator", "\u00A78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"); // §8────────
        this.updateIntervalTicks = sec.getInt("update_interval_ticks", 20);
    }

    public String brandTitle() { return brandTitle; }
    public String brandSub() { return brandSub; }
    public String domain() { return domain; }
    public boolean gradientTitle() { return gradientTitle; }
    public String separator() { return separator; }
    public int updateIntervalTicks() { return updateIntervalTicks; }
}
