package fr.heneria.nexus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Defines global UI colors and message prefixes to keep a coherent style
 * across all commands and interfaces.
 */
public final class Theme {

    private Theme() {
        // Utility class
    }

    public static final NamedTextColor COLOR_PRIMARY = NamedTextColor.AQUA;
    public static final NamedTextColor COLOR_SUCCESS = NamedTextColor.GREEN;
    public static final NamedTextColor COLOR_ERROR = NamedTextColor.RED;

    public static final Component PREFIX_MAIN = Component.text("[Nexus] ", COLOR_PRIMARY);
}
