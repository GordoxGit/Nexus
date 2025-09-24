package com.heneria.nexus.hologram;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.scheduler.GamePhase;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.util.NexusLogger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Implémentation par défaut du {@link HoloService} basée sur les entités TextDisplay.
 */
public final class HoloServiceImpl implements HoloService {

    private static final String CONFIG_FILE = "holograms.yml";
    private static final String UPDATE_TASK_ID = "hologram-update";

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final RingScheduler scheduler;
    private final AtomicReference<CoreConfig.HologramSettings> settingsRef;
    private final HologramPool pool;
    private final HologramFactory factory;
    private final ConcurrentMap<String, HologramEntry> holograms = new ConcurrentHashMap<>();
    private final AtomicBoolean placeholderFailureLogged = new AtomicBoolean();
    private final boolean placeholderApiAvailable;

    public HoloServiceImpl(JavaPlugin plugin,
                           NexusLogger logger,
                           CoreConfig coreConfig,
                           RingScheduler scheduler,
                           Boolean placeholderApiAvailable) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(coreConfig, "coreConfig");
        this.settingsRef = new AtomicReference<>(coreConfig.hologramSettings());
        this.pool = new HologramPool(plugin, logger, coreConfig.hologramSettings());
        this.placeholderApiAvailable = Boolean.TRUE.equals(placeholderApiAvailable);
        UnaryOperator<String> resolver = buildPlaceholderResolver();
        this.factory = new HologramFactory(plugin, logger, pool, settingsRef::get, resolver);
    }

    @Override
    public CompletableFuture<Void> start() {
        scheduler.registerTask(UPDATE_TASK_ID, hzToTicks(settingsRef.get().updateHz()),
                EnumSet.allOf(GamePhase.class), this::tick);
        loadFromConfig();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        scheduler.unregisterTask(UPDATE_TASK_ID);
        holograms.values().forEach(entry -> entry.hologram.destroy());
        holograms.clear();
        pool.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized void loadFromConfig() {
        removePersistent();
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!file.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            logger.warn("Lecture de " + CONFIG_FILE + " impossible", exception);
            return;
        }
        ConfigurationSection hologramSection = yaml.getConfigurationSection("holograms");
        if (hologramSection == null) {
            logger.info("Chargé 0 hologrammes depuis " + CONFIG_FILE + ".");
            return;
        }
        int loaded = 0;
        for (String id : hologramSection.getKeys(false)) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (holograms.containsKey(id)) {
                logger.warn("Hologramme " + id + " déjà chargé, définition ignorée");
                continue;
            }
            ConfigurationSection section = hologramSection.getConfigurationSection(id);
            if (section == null) {
                logger.warn("Définition invalide pour " + id + " : section manquante");
                continue;
            }
            String worldName = section.getString("world");
            if (worldName == null || worldName.isBlank()) {
                logger.warn("Hologramme " + id + " ignoré: monde non défini");
                continue;
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                logger.warn("Hologramme " + id + " ignoré: monde inexistant (" + worldName + ")");
                continue;
            }
            double x = section.getDouble("x");
            double y = section.getDouble("y");
            double z = section.getDouble("z");
            float yaw = (float) section.getDouble("yaw", 0D);
            float pitch = (float) section.getDouble("pitch", 0D);
            List<String> lines = section.getStringList("lines");
            String group = section.getString("group", "default");
            Location location = new Location(world, x, y, z, yaw, pitch);
            registerFromConfig(id, location, lines, group);
            loaded++;
        }
        logger.info("Chargé " + loaded + " hologrammes depuis " + CONFIG_FILE + ".");
    }

    @Override
    public Hologram createHologram(String id, Location location, List<String> lines) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(lines, "lines");
        if (holograms.containsKey(id)) {
            throw new IllegalArgumentException("Hologramme " + id + " déjà enregistré");
        }
        Hologram hologram = factory.create(id, location.clone(), List.copyOf(lines), "default");
        holograms.put(id, new HologramEntry(hologram, false));
        warnPlaceholderIfNeeded(hologram);
        return hologram;
    }

    @Override
    public Optional<Hologram> getHologram(String id) {
        HologramEntry entry = holograms.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.hologram);
    }

    @Override
    public void removeHologram(String id) {
        HologramEntry entry = holograms.remove(id);
        if (entry == null) {
            return;
        }
        entry.hologram.destroy();
    }

    @Override
    public Collection<Hologram> holograms() {
        return holograms.values().stream().map(entry -> entry.hologram).toList();
    }

    @Override
    public void applySettings(CoreConfig.HologramSettings settings) {
        Objects.requireNonNull(settings, "settings");
        settingsRef.set(settings);
        pool.updateLimits(settings);
        scheduler.updateTaskInterval(UPDATE_TASK_ID, hzToTicks(settings.updateHz()));
        holograms.values().forEach(entry -> entry.hologram.applySettings(settings));
    }

    @Override
    public Diagnostics diagnostics() {
        return new Diagnostics(holograms.size(), pool.pooledTextDisplays(), pool.pooledInteractions());
    }

    private void registerFromConfig(String id, Location location, List<String> lines, String group) {
        List<String> sanitized = lines == null ? List.of() : List.copyOf(lines);
        Hologram hologram = factory.create(id, location, sanitized, group);
        holograms.put(id, new HologramEntry(hologram, true));
        warnPlaceholderIfNeeded(hologram);
    }

    private void removePersistent() {
        for (Map.Entry<String, HologramEntry> entry : new ArrayList<>(holograms.entrySet())) {
            if (!entry.getValue().persistent) {
                continue;
            }
            entry.getValue().hologram.destroy();
            holograms.remove(entry.getKey());
        }
    }

    private void tick() {
        holograms.values().forEach(entry -> entry.hologram.tick());
    }

    private long hzToTicks(int hz) {
        return Math.max(1L, Math.round(20.0D / Math.max(1, hz)));
    }

    private UnaryOperator<String> buildPlaceholderResolver() {
        if (!placeholderApiAvailable) {
            return value -> value;
        }
        return value -> {
            try {
                return PlaceholderAPI.setPlaceholders(null, value);
            } catch (Throwable throwable) {
                if (placeholderFailureLogged.compareAndSet(false, true)) {
                    logger.warn("Erreur lors de l'évaluation PlaceholderAPI", throwable);
                }
                return value;
            }
        };
    }

    private void warnPlaceholderIfNeeded(Hologram hologram) {
        if (!placeholderApiAvailable && hologram.hasDynamicLines()) {
            logger.warn("Hologramme " + hologram.id() + " utilise des placeholders sans PlaceholderAPI.");
        }
    }

    private record HologramEntry(Hologram hologram, boolean persistent) {
        private HologramEntry {
            Objects.requireNonNull(hologram, "hologram");
        }
    }
}
