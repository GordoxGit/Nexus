package com.heneria.nexus.hologram;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.util.NexusLogger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Pool réutilisable d'entités Display afin de limiter les allocations runtime.
 */
final class HologramPool {

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final ConcurrentLinkedDeque<TextDisplay> textPool = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Interaction> interactionPool = new ConcurrentLinkedDeque<>();
    private final AtomicInteger maxTextDisplays;
    private final AtomicInteger maxInteractions;

    HologramPool(JavaPlugin plugin, NexusLogger logger, CoreConfig.HologramSettings settings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(settings, "settings");
        this.maxTextDisplays = new AtomicInteger(Math.max(0, settings.maxPooledTextDisplays()));
        this.maxInteractions = new AtomicInteger(Math.max(0, settings.maxPooledInteractions()));
    }

    TextDisplay borrowTextDisplay(Location location, double viewRange) {
        Objects.requireNonNull(location, "location");
        TextDisplay display;
        while ((display = textPool.pollFirst()) != null) {
            if (!display.isValid() || display.isDead()) {
                continue;
            }
            resetDisplay(display, location, viewRange);
            return display;
        }
        return spawnDisplay(location, viewRange);
    }

    Interaction borrowInteraction(Location location) {
        Objects.requireNonNull(location, "location");
        Interaction interaction;
        while ((interaction = interactionPool.pollFirst()) != null) {
            if (!interaction.isValid() || interaction.isDead()) {
                continue;
            }
            resetInteraction(interaction, location);
            return interaction;
        }
        return spawnInteraction(location);
    }

    void releaseTextDisplay(TextDisplay display, Collection<Player> viewers) {
        if (display == null) {
            return;
        }
        hideFrom(viewers, display);
        hideFromAll(display);
        display.setText(Component.empty());
        display.setGlowColorOverride(null);
        if (!display.isValid() || textPool.size() >= maxTextDisplays.get()) {
            display.remove();
            return;
        }
        textPool.addLast(display);
    }

    void releaseInteraction(Interaction interaction, Collection<Player> viewers) {
        if (interaction == null) {
            return;
        }
        hideFrom(viewers, interaction);
        hideFromAll(interaction);
        interaction.setInteractionHeight(0F);
        interaction.setInteractionWidth(0F);
        if (!interaction.isValid() || interactionPool.size() >= maxInteractions.get()) {
            interaction.remove();
            return;
        }
        interactionPool.addLast(interaction);
    }

    void clear() {
        textPool.forEach(entity -> {
            try {
                entity.remove();
            } catch (Exception exception) {
                logger.warn("Impossible de retirer un TextDisplay du pool", exception);
            }
        });
        textPool.clear();
        interactionPool.forEach(entity -> {
            try {
                entity.remove();
            } catch (Exception exception) {
                logger.warn("Impossible de retirer une Interaction du pool", exception);
            }
        });
        interactionPool.clear();
    }

    void updateLimits(CoreConfig.HologramSettings settings) {
        Objects.requireNonNull(settings, "settings");
        maxTextDisplays.set(Math.max(0, settings.maxPooledTextDisplays()));
        maxInteractions.set(Math.max(0, settings.maxPooledInteractions()));
        trimPool(textPool, maxTextDisplays.get());
        trimPool(interactionPool, maxInteractions.get());
    }

    int pooledTextDisplays() {
        return textPool.size();
    }

    int pooledInteractions() {
        return interactionPool.size();
    }

    private TextDisplay spawnDisplay(Location location, double viewRange) {
        return location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setShadowed(false);
            entity.setSeeThrough(true);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setViewRange((float) viewRange);
            entity.setRotation(location.getYaw(), location.getPitch());
            entity.setText(Component.empty());
            try {
                entity.setVisibleByDefault(false);
            } catch (NoSuchMethodError ignored) {
                // Méthode absente selon la version Paper, on se contente de masquer via hideEntity.
            }
            hideFromAll(entity);
        });
    }

    private void resetDisplay(TextDisplay display, Location location, double viewRange) {
        display.teleport(location);
        display.setViewRange((float) viewRange);
        display.setRotation(location.getYaw(), location.getPitch());
        display.setText(Component.empty());
    }

    private Interaction spawnInteraction(Location location) {
        return location.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setResponsive(false);
            entity.setInteractionHeight(0F);
            entity.setInteractionWidth(0F);
            try {
                entity.setVisibleByDefault(false);
            } catch (NoSuchMethodError ignored) {
                // Ignore lorsque l'API ne l'expose pas.
            }
            hideFromAll(entity);
        });
    }

    private void resetInteraction(Interaction interaction, Location location) {
        interaction.teleport(location);
        interaction.setInteractionHeight(0F);
        interaction.setInteractionWidth(0F);
    }

    private void hideFrom(Collection<Player> viewers, org.bukkit.entity.Entity entity) {
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        for (Player player : viewers) {
            if (player == null) {
                continue;
            }
            player.hideEntity(plugin, entity);
        }
    }

    private void hideFromAll(org.bukkit.entity.Entity entity) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.hideEntity(plugin, entity);
        }
    }

    private <T extends org.bukkit.entity.Entity> void trimPool(ConcurrentLinkedDeque<T> pool, int maxSize) {
        if (maxSize < 0) {
            maxSize = 0;
        }
        if (pool.size() <= maxSize) {
            return;
        }
        List<T> removed = new ArrayList<>();
        Iterator<T> iterator = pool.descendingIterator();
        while (pool.size() > maxSize && iterator.hasNext()) {
            T entity = iterator.next();
            iterator.remove();
            removed.add(entity);
        }
        removed.forEach(entity -> {
            try {
                entity.remove();
            } catch (Exception exception) {
                logger.warn("Impossible de retirer une entité du pool", exception);
            }
        });
    }
}
