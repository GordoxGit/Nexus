package com.heneria.nexus.service.permissions;

import com.heneria.nexus.service.api.ArenaMode;
import com.heneria.nexus.util.NexusLogger;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.MutableContextSet;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bridges Nexus arena state with LuckPerms contextual permissions.
 */
public final class NexusContextManager implements ContextCalculator<Player>, AutoCloseable {

    private static final String CONTEXT_KEY = "nexus-mode";

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final LuckPerms luckPerms;
    private final Map<UUID, ArenaMode> arenaContexts = new ConcurrentHashMap<>();
    private final boolean enabled;

    public NexusContextManager(JavaPlugin plugin, NexusLogger logger, LuckPerms luckPerms) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.luckPerms = luckPerms;
        this.enabled = luckPerms != null;
        if (enabled) {
            luckPerms.getContextManager().registerCalculator(this);
            logger.info("Gestionnaire de contextes LuckPerms initialisé.");
        } else {
            logger.debug(() -> "LuckPerms absent, gestionnaire de contextes désactivé");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void addPlayerToArenaContext(UUID playerId, ArenaMode mode) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mode, "mode");
        if (!enabled) {
            return;
        }
        arenaContexts.put(playerId, mode);
        signalContextUpdate(playerId);
    }

    public void clearPlayerArenaContext(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!enabled) {
            return;
        }
        arenaContexts.remove(playerId);
        signalContextUpdate(playerId);
    }

    public void clearAll() {
        if (!enabled) {
            return;
        }
        arenaContexts.clear();
    }

    @Override
    public void calculate(Player target, MutableContextSet accumulator) {
        if (!enabled) {
            return;
        }
        ArenaMode mode = arenaContexts.get(target.getUniqueId());
        if (mode == null) {
            return;
        }
        accumulator.add(CONTEXT_KEY, toContextValue(mode));
    }

    @Override
    public void close() {
        if (!enabled) {
            return;
        }
        luckPerms.getContextManager().unregisterCalculator(this);
        arenaContexts.clear();
    }

    private void signalContextUpdate(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            luckPerms.getContextManager().signalContextUpdate(player);
        }
    }

    private String toContextValue(ArenaMode mode) {
        return switch (mode) {
            case CASUAL -> "casual";
            case COMPETITIVE -> "ranked";
        };
    }
}
