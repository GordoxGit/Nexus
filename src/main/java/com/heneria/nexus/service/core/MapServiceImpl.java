package com.heneria.nexus.service.core;

import com.heneria.nexus.service.ExecutorPools;
import com.heneria.nexus.service.api.MapDefinition;
import com.heneria.nexus.service.api.MapLoadException;
import com.heneria.nexus.service.api.MapQuery;
import com.heneria.nexus.service.api.MapService;
import com.heneria.nexus.service.api.ValidationReport;
import com.heneria.nexus.util.NexusLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Default in-memory implementation of {@link MapService}.
 */
public final class MapServiceImpl implements MapService {

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final ExecutorService computeExecutor;
    private final AtomicReference<Map<String, MapDefinition>> catalog = new AtomicReference<>(Map.of());
    private final AtomicReference<ValidationReport> lastValidation = new AtomicReference<>();

    public MapServiceImpl(JavaPlugin plugin, NexusLogger logger, ExecutorPools executorPools) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.computeExecutor = Objects.requireNonNull(executorPools, "executorPools").computeExecutor();
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                loadCatalog();
            } catch (MapLoadException exception) {
                throw new RuntimeException(exception);
            }
        }, computeExecutor);
    }

    @Override
    public void loadCatalog() throws MapLoadException {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path mapsFile = dataFolder.resolve("maps.yml");
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(mapsFile.toFile());
        ConfigurationSection root = configuration.getConfigurationSection("maps");
        Map<String, MapDefinition> newCatalog = new ConcurrentHashMap<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                String displayName = section.getString("display", key);
                Path mapFolder = dataFolder.resolve("maps").resolve(key);
                Map<String, Object> metadata = new HashMap<>();
                section.getValues(false).forEach(metadata::put);
                newCatalog.put(key.toLowerCase(Locale.ROOT), new MapDefinition(key, displayName, mapFolder, Map.copyOf(metadata)));
            }
        }
        catalog.set(Collections.unmodifiableMap(newCatalog));
        logger.info("Catalogue des maps chargé (%d entrées)".formatted(newCatalog.size()));
    }

    @Override
    public void reload() throws MapLoadException {
        loadCatalog();
    }

    @Override
    public Optional<MapDefinition> getMap(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(catalog.get().get(id.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Collection<MapDefinition> list(MapQuery query) {
        MapQuery effectiveQuery = query == null ? MapQuery.any() : query;
        return catalog.get().values().stream()
                .filter(definition -> filterByMode(definition, effectiveQuery))
                .collect(Collectors.toUnmodifiableList());
    }

    private boolean filterByMode(MapDefinition definition, MapQuery query) {
        if (query.mode().isEmpty()) {
            return true;
        }
        Object modes = definition.metadata().get("modes");
        if (modes instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .anyMatch(value -> value.equals(query.mode().get().name()));
        }
        return true;
    }

    @Override
    public ValidationReport validate(String mapId) {
        MapDefinition definition = getMap(mapId).orElse(null);
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (definition == null) {
            errors.add("Map inconnue : " + mapId);
            ValidationReport report = ValidationReport.failure(warnings, errors);
            lastValidation.set(report);
            return report;
        }
        Path folder = definition.folder();
        if (!Files.exists(folder)) {
            errors.add("Dossier introuvable : " + folder);
        } else {
            Path layout = folder.resolve("map.yml");
            if (!Files.exists(layout)) {
                warnings.add("map.yml manquant pour " + definition.id());
            }
            Path schematic = folder.resolve("world.schem");
            if (!Files.exists(schematic)) {
                warnings.add("world.schem absent pour " + definition.id());
            }
        }
        ValidationReport report = errors.isEmpty()
                ? ValidationReport.success(warnings)
                : ValidationReport.failure(warnings, errors);
        lastValidation.set(report);
        return report;
    }

    public Optional<ValidationReport> lastValidation() {
        return Optional.ofNullable(lastValidation.get());
    }
}
