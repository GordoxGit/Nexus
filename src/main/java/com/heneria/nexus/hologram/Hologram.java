package com.heneria.nexus.hologram;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.util.NexusLogger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Représente un hologramme multi-lignes rendu via les entités Display.
 */
public final class Hologram {

    private final String id;
    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final HologramPool pool;
    private final MiniMessage miniMessage;
    private final UnaryOperator<String> placeholderResolver;
    private final List<Line> lines = new ArrayList<>();
    private final List<TextDisplay> displays = new ArrayList<>();
    private final Set<UUID> viewers = new CopyOnWriteArraySet<>();

    private volatile Location baseLocation;
    private volatile String group = "default";
    private volatile double lineSpacing;
    private volatile double viewRange;
    private volatile int maxVisible;
    private volatile boolean disposed;
    private Interaction interaction;

    Hologram(String id,
             JavaPlugin plugin,
             NexusLogger logger,
             HologramPool pool,
             MiniMessage miniMessage,
             UnaryOperator<String> placeholderResolver,
             CoreConfig.HologramSettings settings) {
        this.id = Objects.requireNonNull(id, "id");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pool = Objects.requireNonNull(pool, "pool");
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
        this.placeholderResolver = Objects.requireNonNull(placeholderResolver, "placeholderResolver");
        applySettings(settings);
    }

    public String id() {
        return id;
    }

    public String group() {
        return group;
    }

    public void setGroup(String group) {
        if (group == null || group.isBlank()) {
            this.group = "default";
            return;
        }
        this.group = group.toLowerCase(Locale.ROOT);
    }

    public Location location() {
        return baseLocation == null ? null : baseLocation.clone();
    }

    public List<String> lines() {
        return lines.stream().map(Line::raw).toList();
    }

    void applySettings(CoreConfig.HologramSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.lineSpacing = settings.lineSpacing();
        this.viewRange = settings.viewRange();
        this.maxVisible = settings.maxVisiblePerInstance();
        for (TextDisplay display : displays) {
            display.setViewRange((float) viewRange);
        }
        updatePositions();
    }

    public void teleport(Location location) {
        ensureNotDisposed();
        Objects.requireNonNull(location, "location");
        this.baseLocation = location.clone();
        updatePositions();
    }

    public void updateLines(List<String> newLines) {
        ensureNotDisposed();
        Objects.requireNonNull(newLines, "newLines");
        Location location = Objects.requireNonNull(baseLocation, "location");
        List<String> sanitized = newLines.stream().map(line -> line == null ? "" : line).toList();
        for (int index = 0; index < sanitized.size(); index++) {
            if (index < lines.size()) {
                lines.get(index).updateRaw(sanitized.get(index));
                continue;
            }
            TextDisplay display = pool.borrowTextDisplay(location, viewRange);
            displays.add(display);
            Line line = new Line(sanitized.get(index));
            lines.add(line);
        }
        while (lines.size() > sanitized.size()) {
            int lastIndex = lines.size() - 1;
            Line removed = lines.remove(lastIndex);
            TextDisplay display = displays.remove(lastIndex);
            pool.releaseTextDisplay(display, resolveOnlineViewers());
        }
        updatePositions();
        ensureInteraction();
        lines.forEach(Line::markDirty);
        tick();
    }

    public void showTo(Player player) {
        ensureNotDisposed();
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) {
            return;
        }
        if (baseLocation == null || !player.getWorld().equals(baseLocation.getWorld())) {
            return;
        }
        if (!canView(player)) {
            return;
        }
        cleanupViewers();
        UUID uuid = player.getUniqueId();
        if (!viewers.contains(uuid)) {
            if (viewers.size() >= maxVisible) {
                return;
            }
            viewers.add(uuid);
        }
        for (TextDisplay display : displays) {
            player.showEntity(plugin, display);
        }
        if (interaction != null) {
            player.showEntity(plugin, interaction);
        }
        tick();
    }

    public void hideFrom(Player player) {
        Objects.requireNonNull(player, "player");
        UUID uuid = player.getUniqueId();
        if (!viewers.remove(uuid)) {
            return;
        }
        for (TextDisplay display : displays) {
            player.hideEntity(plugin, display);
        }
        if (interaction != null) {
            player.hideEntity(plugin, interaction);
        }
    }

    public void tick() {
        if (disposed || baseLocation == null || lines.isEmpty()) {
            return;
        }
        List<Player> activeViewers = cleanupViewers();
        boolean hasViewers = !activeViewers.isEmpty();
        boolean needsUpdate = false;
        for (Line line : lines) {
            if (line.needsUpdate(hasViewers)) {
                needsUpdate = true;
                break;
            }
        }
        if (!needsUpdate) {
            return;
        }
        renderLines();
    }

    boolean hasDynamicLines() {
        return lines.stream().anyMatch(Line::dynamic);
    }

    void destroy() {
        if (disposed) {
            return;
        }
        disposed = true;
        List<Player> activeViewers = resolveOnlineViewers();
        for (Player player : activeViewers) {
            for (TextDisplay display : displays) {
                player.hideEntity(plugin, display);
            }
            if (interaction != null) {
                player.hideEntity(plugin, interaction);
            }
        }
        viewers.clear();
        for (TextDisplay display : displays) {
            pool.releaseTextDisplay(display, activeViewers);
        }
        displays.clear();
        lines.clear();
        if (interaction != null) {
            pool.releaseInteraction(interaction, activeViewers);
            interaction = null;
        }
    }

    private boolean canView(Player player) {
        return player.hasPermission("nexus.holo.view." + group)
                || player.hasPermission("nexus.holo.view.*")
                || player.hasPermission("nexus.holo.manage");
    }

    private List<Player> cleanupViewers() {
        List<Player> players = new ArrayList<>();
        Iterator<UUID> iterator = viewers.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            if (baseLocation != null && !player.getWorld().equals(baseLocation.getWorld())) {
                for (TextDisplay display : displays) {
                    player.hideEntity(plugin, display);
                }
                if (interaction != null) {
                    player.hideEntity(plugin, interaction);
                }
                iterator.remove();
                continue;
            }
            players.add(player);
        }
        return players;
    }

    private List<Player> resolveOnlineViewers() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : viewers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    private void ensureInteraction() {
        if (lines.isEmpty()) {
            if (interaction != null) {
                pool.releaseInteraction(interaction, resolveOnlineViewers());
                interaction = null;
            }
            return;
        }
        if (interaction == null) {
            interaction = pool.borrowInteraction(baseLocation);
        }
        updateInteractionBounds();
    }

    private void updatePositions() {
        if (baseLocation == null || displays.isEmpty()) {
            return;
        }
        Location origin = baseLocation.clone();
        for (int index = 0; index < displays.size(); index++) {
            TextDisplay display = displays.get(index);
            Location target = origin.clone().subtract(0D, lineSpacing * index, 0D);
            display.teleport(target);
        }
        updateInteractionBounds();
    }

    private void updateInteractionBounds() {
        if (interaction == null || baseLocation == null || lines.isEmpty()) {
            return;
        }
        double height = Math.max(0.3D, lineSpacing * lines.size());
        Location center = baseLocation.clone().subtract(0D, lineSpacing * (lines.size() - 1) / 2D, 0D);
        interaction.teleport(center);
        interaction.setInteractionHeight((float) height);
        interaction.setInteractionWidth(1.4F);
    }

    private void renderLines() {
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (!line.shouldRender()) {
                continue;
            }
            String resolved;
            try {
                resolved = placeholderResolver.apply(line.raw);
            } catch (Exception exception) {
                logger.warn("PlaceholderAPI a échoué pour l'hologramme " + id, exception);
                resolved = line.raw;
            }
            if (!line.dirty && resolved.equals(line.lastResolved)) {
                continue;
            }
            Component component = deserialize(resolved);
            displays.get(index).setText(component);
            line.onRendered(resolved);
        }
    }

    private Component deserialize(String value) {
        try {
            return miniMessage.deserialize(value);
        } catch (Exception exception) {
            logger.warn("MiniMessage invalide pour l'hologramme " + id + ": " + value, exception);
            return Component.text(value);
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Hologramme supprimé");
        }
    }

    private static final class Line {

        private String raw;
        private boolean dirty = true;
        private boolean dynamic;
        private String lastResolved = "";

        Line(String raw) {
            updateRaw(raw);
        }

        String raw() {
            return raw;
        }

        void updateRaw(String value) {
            String sanitized = value == null ? "" : value;
            if (!sanitized.equals(this.raw)) {
                this.raw = sanitized;
                this.dirty = true;
            }
            this.dynamic = sanitized.contains("%");
        }

        void markDirty() {
            this.dirty = true;
        }

        boolean needsUpdate(boolean hasViewers) {
            return dirty || (dynamic && hasViewers);
        }

        boolean shouldRender() {
            return dirty || dynamic;
        }

        void onRendered(String resolved) {
            this.lastResolved = resolved;
            this.dirty = false;
        }

        boolean dynamic() {
            return dynamic;
        }
    }
}
