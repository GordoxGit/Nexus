package com.heneria.nexus.service.core;

import com.heneria.nexus.api.AntiSpawnKillService;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.util.NexusLogger;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Default implementation of the anti spawn kill protection service.
 */
public final class AntiSpawnKillServiceImpl implements AntiSpawnKillService {

    private final NexusLogger logger;
    private final ExecutorManager executorManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final AtomicReference<CoreConfig.ArenaSettings.SpawnProtectionSettings> settingsRef;
    private final ConcurrentMap<UUID, ProtectionState> protections = new ConcurrentHashMap<>();

    public AntiSpawnKillServiceImpl(JavaPlugin plugin,
                                    NexusLogger logger,
                                    ExecutorManager executorManager,
                                    CoreConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        Objects.requireNonNull(config, "config");
        this.settingsRef = new AtomicReference<>(config.arenaSettings().spawnProtection());
    }

    @Override
    public void applyProtection(Player player) {
        Objects.requireNonNull(player, "player");
        CoreConfig.ArenaSettings.SpawnProtectionSettings settings = settingsRef.get();
        if (!settings.enabled()) {
            revokeProtection(player.getUniqueId());
            return;
        }
        revokeProtection(player.getUniqueId());

        int durationTicks = Math.max(1, (int) Math.ceil(settings.duration().toMillis() / 50.0));
        PotionEffect resistance = new PotionEffect(PotionEffectType.RESISTANCE, durationTicks,
                settings.resistanceAmplifier(), false, false, true);
        player.addPotionEffect(resistance, true);
        boolean glowApplied = false;
        if (settings.glow()) {
            PotionEffect glowing = new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0, false, false, false);
            player.addPotionEffect(glowing, true);
            glowApplied = true;
        }
        Particle particle = settings.particle();
        if (particle != null) {
            spawnParticle(player, particle);
        }
        long expiresAt = System.currentTimeMillis() + settings.duration().toMillis();
        ProtectionState state = new ProtectionState(expiresAt, glowApplied);
        ProtectionState previous = protections.put(player.getUniqueId(), state);
        if (previous != null) {
            previous.cancel();
        }
        long interval = Math.max(1L, settings.actionBarIntervalTicks());
        BukkitTask task = executorManager.mainThread().runRepeating(() -> tickProtection(player.getUniqueId()), 0L, interval);
        state.setTask(task);
        tickProtection(player.getUniqueId());
    }

    @Override
    public void revokeProtection(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ProtectionState state = protections.remove(playerId);
        if (state == null) {
            return;
        }
        state.cancel();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.RESISTANCE);
            if (state.glowApplied()) {
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
    }

    @Override
    public boolean isProtected(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return protections.containsKey(playerId);
    }

    @Override
    public void applySettings(CoreConfig.ArenaSettings.SpawnProtectionSettings settings) {
        settingsRef.set(Objects.requireNonNull(settings, "settings"));
        if (!settings.enabled()) {
            protections.keySet().forEach(this::revokeProtection);
        }
        protections.values().forEach(ProtectionState::resetTemplateCache);
    }

    private void tickProtection(UUID playerId) {
        ProtectionState state = protections.get(playerId);
        if (state == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            revokeProtection(playerId);
            return;
        }
        CoreConfig.ArenaSettings.SpawnProtectionSettings settings = settingsRef.get();
        long remaining = state.expiresAt() - System.currentTimeMillis();
        if (remaining <= 0L) {
            revokeProtection(playerId);
            return;
        }
        player.sendActionBar(renderActionBar(settings, remaining, state));
    }

    private Component renderActionBar(CoreConfig.ArenaSettings.SpawnProtectionSettings settings,
                                      long remainingMillis,
                                      ProtectionState state) {
        long seconds = Math.max(0L, (long) Math.ceil(remainingMillis / 1000.0));
        String template = settings.actionBarMessage();
        try {
            return miniMessage.deserialize(template, Placeholder.unparsed("seconds", Long.toString(seconds)));
        } catch (Exception exception) {
            if (state.markInvalidTemplate(template)) {
                logger.warn("MiniMessage invalide pour l'action bar de protection: " + exception.getMessage());
            }
            return Component.text("Invulnérable (" + seconds + "s)", NamedTextColor.GOLD);
        }
    }

    private void spawnParticle(Player player, Particle particle) {
        Location location = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(particle, location, 20, 0.3, 0.6, 0.3, 0.01);
    }

    private static final class ProtectionState {
        private final long expiresAt;
        private final boolean glowApplied;
        private BukkitTask task;
        private String lastInvalidTemplate;

        ProtectionState(long expiresAt, boolean glowApplied) {
            this.expiresAt = expiresAt;
            this.glowApplied = glowApplied;
        }

        long expiresAt() {
            return expiresAt;
        }

        boolean glowApplied() {
            return glowApplied;
        }

        void setTask(BukkitTask task) {
            this.task = task;
        }

        void cancel() {
            if (task != null) {
                task.cancel();
            }
        }

        boolean markInvalidTemplate(String template) {
            if (Objects.equals(lastInvalidTemplate, template)) {
                return false;
            }
            lastInvalidTemplate = template;
            return true;
        }

        void resetTemplateCache() {
            lastInvalidTemplate = null;
        }
    }
}
