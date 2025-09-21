package com.heneria.nexus.hologram;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.util.NexusLogger;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Fabrique centralisant l'initialisation des hologrammes.
 */
final class HologramFactory {

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final HologramPool pool;
    private final Supplier<CoreConfig.HologramSettings> settingsSupplier;
    private final UnaryOperator<String> placeholderResolver;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    HologramFactory(JavaPlugin plugin,
                    NexusLogger logger,
                    HologramPool pool,
                    Supplier<CoreConfig.HologramSettings> settingsSupplier,
                    UnaryOperator<String> placeholderResolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pool = Objects.requireNonNull(pool, "pool");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.placeholderResolver = Objects.requireNonNull(placeholderResolver, "placeholderResolver");
    }

    Hologram create(String id, Location location, List<String> lines, String group) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        Hologram hologram = new Hologram(id, plugin, logger, pool, miniMessage, placeholderResolver, settingsSupplier.get());
        hologram.setGroup(group);
        hologram.teleport(location);
        if (lines != null && !lines.isEmpty()) {
            hologram.updateLines(lines);
        }
        return hologram;
    }
}
