package com.heneria.nexus.service.core;

import com.heneria.nexus.api.MapDefinition;
import com.heneria.nexus.api.MapLoadException;
import com.heneria.nexus.api.MapQuery;
import com.heneria.nexus.api.MapService;
import com.heneria.nexus.api.MapValidatorService;
import com.heneria.nexus.api.ValidationReport;
import com.heneria.nexus.api.map.MapBlueprint;
import com.heneria.nexus.api.map.MapBlueprint.MapAsset;
import com.heneria.nexus.api.map.MapBlueprint.MapInteractive;
import com.heneria.nexus.api.map.MapBlueprint.MapNexus;
import com.heneria.nexus.api.map.MapBlueprint.MapRegion;
import com.heneria.nexus.api.map.MapBlueprint.MapRules;
import com.heneria.nexus.api.map.MapBlueprint.MapTeam;
import com.heneria.nexus.api.map.MapBlueprint.MapVector;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.util.NexusLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Default in-memory implementation of {@link MapService}.
 */
public final class MapServiceImpl implements MapService {

    private static final Set<String> KNOWN_BLUEPRINT_KEYS = Set.of("asset", "rules", "teams", "regions", "interactives");

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final ExecutorManager executorManager;
    private final MapValidatorService validatorService;
    private final AtomicReference<Map<String, MapDefinition>> catalog = new AtomicReference<>(Map.of());
    private final AtomicReference<ValidationReport> lastValidation = new AtomicReference<>();

    public MapServiceImpl(JavaPlugin plugin,
                          NexusLogger logger,
                          ExecutorManager executorManager,
                          MapValidatorService validatorService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.validatorService = Objects.requireNonNull(validatorService, "validatorService");
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return executorManager.runCompute(() -> {
            try {
                loadCatalog();
            } catch (MapLoadException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    @Override
    public void loadCatalog() throws MapLoadException {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path mapsFile = dataFolder.resolve("maps.yml");
        YamlConfiguration configuration = loadYaml(mapsFile);
        ConfigurationSection root = configuration.getConfigurationSection("maps");
        Map<String, MapDefinition> validCatalog = new ConcurrentHashMap<>();
        int totalEntries = 0;
        if (root != null) {
            for (String key : root.getKeys(false)) {
                totalEntries++;
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    logger.warn("[maps.yml] Entrée '%s' ignorée : section invalide".formatted(key));
                    continue;
                }
                String identifier = key.toLowerCase(Locale.ROOT);
                String displayName = section.getString("display", key);
                Path mapFolder = dataFolder.resolve("maps").resolve(key);
                Map<String, Object> metadata = extractMetadata(section);
                if (!Files.exists(mapFolder)) {
                    ValidationReport report = ValidationReport.failure(List.of(),
                            List.of("[maps.yml] Carte '%s': le dossier %s est introuvable".formatted(key, mapFolder)));
                    logReport(key, displayName, report);
                    continue;
                }
                MapBlueprint blueprint;
                try {
                    blueprint = loadBlueprint(key, mapFolder);
                } catch (IOException exception) {
                    ValidationReport report = ValidationReport.failure(List.of(),
                            List.of("[maps.yml] Carte '%s': lecture de map.yml impossible (%s)".formatted(key, exception.getMessage())));
                    logger.error("Lecture de map.yml impossible pour la carte '" + key + "'", exception);
                    logReport(key, displayName, report);
                    continue;
                }
                MapDefinition definition = new MapDefinition(key, displayName, mapFolder, metadata, blueprint);
                ValidationReport report = validatorService.validate(definition, blueprint);
                logReport(key, displayName, report);
                if (report.valid()) {
                    validCatalog.put(identifier, definition);
                }
            }
        }
        catalog.set(Collections.unmodifiableMap(validCatalog));
        logger.info("Catalogue des maps chargé : %d valide(s) / %d entrée(s)".formatted(validCatalog.size(), totalEntries));
    }

    @Override
    public void reload() throws MapLoadException {
        try {
            executorManager.runCompute(() -> {
                try {
                    loadCatalog();
                } catch (MapLoadException exception) {
                    throw new CompletionException(exception);
                }
            }).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof MapLoadException loadException) {
                throw loadException;
            }
            throw exception;
        }
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
        if (mapId == null || mapId.isBlank()) {
            ValidationReport report = ValidationReport.failure(List.of(),
                    List.of("Identifiant de carte vide"));
            lastValidation.set(report);
            return report;
        }
        Path mapFolder = plugin.getDataFolder().toPath().resolve("maps").resolve(mapId);
        MapBlueprint blueprint;
        try {
            blueprint = loadBlueprint(mapId, mapFolder);
        } catch (IOException exception) {
            ValidationReport report = ValidationReport.failure(List.of(),
                    List.of("Lecture de map.yml impossible pour '%s': %s".formatted(mapId, exception.getMessage())));
            lastValidation.set(report);
            return report;
        }
        MapDefinition definition = getMap(mapId)
                .orElse(new MapDefinition(mapId, mapId, mapFolder, Map.of(), blueprint));
        ValidationReport report = validatorService.validate(definition, blueprint);
        lastValidation.set(report);
        return report;
    }

    public Optional<ValidationReport> lastValidation() {
        return Optional.ofNullable(lastValidation.get());
    }

    private YamlConfiguration loadYaml(Path file) throws MapLoadException {
        YamlConfiguration configuration = new YamlConfiguration();
        if (!Files.exists(file)) {
            return configuration;
        }
        try {
            configuration.load(file.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            throw new MapLoadException("Impossible de lire " + file.getFileName(), exception);
        }
        return configuration;
    }

    private MapBlueprint loadBlueprint(String mapId, Path mapFolder) throws IOException {
        if (!Files.exists(mapFolder)) {
            return MapBlueprint.missingConfiguration();
        }
        Path mapFile = mapFolder.resolve("map.yml");
        if (!Files.exists(mapFile)) {
            return MapBlueprint.missingConfiguration();
        }
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(mapFile.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IOException("map.yml invalide", exception);
        }
        return parseBlueprint(configuration);
    }

    private MapBlueprint parseBlueprint(YamlConfiguration configuration) {
        MapAsset asset = parseAsset(configuration.getConfigurationSection("asset"));
        MapRules rules = parseRules(configuration.getConfigurationSection("rules"));
        List<MapTeam> teams = parseTeams(configuration.getConfigurationSection("teams"));
        List<MapRegion> regions = parseRegions(configuration.getConfigurationSection("regions"));
        List<MapInteractive> interactives = parseInteractives(configuration.getConfigurationSection("interactives"));
        Map<String, Object> extras = extractExtras(configuration, KNOWN_BLUEPRINT_KEYS);
        return new MapBlueprint(true, asset, rules, teams, regions, interactives, extras);
    }

    private MapAsset parseAsset(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String file = section.getString("file");
        Map<String, Object> properties = extractMetadata(section);
        return new MapAsset(file, properties);
    }

    private MapRules parseRules(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Integer minPlayers = getInteger(section, "min_players");
        Integer maxPlayers = getInteger(section, "max_players");
        Map<String, Object> properties = extractMetadata(section);
        return new MapRules(minPlayers, maxPlayers, properties);
    }

    private List<MapTeam> parseTeams(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<MapTeam> teams = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection teamSection = section.getConfigurationSection(id);
            if (teamSection == null) {
                continue;
            }
            String displayName = teamSection.getString("name", id);
            MapVector spawn = parseVector(teamSection.getConfigurationSection("spawn"));
            MapNexus nexus = parseNexus(teamSection.getConfigurationSection("nexus"));
            Map<String, Object> properties = extractMetadata(teamSection);
            teams.add(new MapTeam(id, displayName, spawn, nexus, properties));
        }
        return List.copyOf(teams);
    }

    private MapNexus parseNexus(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        ConfigurationSection positionSection = section.getConfigurationSection("position");
        MapVector position = positionSection != null ? parseVector(positionSection) : parseVector(section);
        Integer hp = getInteger(section, "hp");
        Map<String, Object> properties = extractMetadata(section);
        return new MapNexus(position, hp, properties);
    }

    private List<MapRegion> parseRegions(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<MapRegion> regions = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection regionSection = section.getConfigurationSection(id);
            if (regionSection == null) {
                continue;
            }
            regions.add(new MapRegion(id, extractMetadata(regionSection)));
        }
        return List.copyOf(regions);
    }

    private List<MapInteractive> parseInteractives(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<MapInteractive> interactives = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection interactiveSection = section.getConfigurationSection(id);
            if (interactiveSection == null) {
                continue;
            }
            interactives.add(new MapInteractive(id, extractMetadata(interactiveSection)));
        }
        return List.copyOf(interactives);
    }

    private MapVector parseVector(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Double x = getDouble(section, "x");
        Double y = getDouble(section, "y");
        Double z = getDouble(section, "z");
        Float yaw = getFloat(section, "yaw");
        Float pitch = getFloat(section, "pitch");
        if (x == null && y == null && z == null && yaw == null && pitch == null) {
            return null;
        }
        return new MapVector(x, y, z, yaw, pitch);
    }

    private Map<String, Object> extractMetadata(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Entry<String, Object> entry : section.getValues(false).entrySet()) {
            result.put(entry.getKey(), convertValue(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private Map<String, Object> extractExtras(ConfigurationSection section, Set<String> excludedKeys) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (excludedKeys.contains(key)) {
                continue;
            }
            result.put(key, convertValue(section.get(key)));
        }
        return Map.copyOf(result);
    }

    private Object convertValue(Object value) {
        if (value instanceof ConfigurationSection configurationSection) {
            return extractMetadata(configurationSection);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((k, v) -> converted.put(String.valueOf(k), convertValue(v)));
            return Map.copyOf(converted);
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>();
            for (Object element : list) {
                converted.add(convertValue(element));
            }
            return List.copyOf(converted);
        }
        return value;
    }

    private Integer getInteger(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            return null;
        }
        Object value = section.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double getDouble(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            return null;
        }
        Object value = section.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Float getFloat(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            return null;
        }
        Object value = section.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String string) {
            try {
                return Float.parseFloat(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void logReport(String mapId, String displayName, ValidationReport report) {
        String label = displayName == null || displayName.isBlank() ? mapId : displayName;
        report.warnings().forEach(warning -> logger.warn("[map:%s] %s".formatted(mapId, warning)));
        report.errors().forEach(error -> logger.error("[map:%s] %s".formatted(mapId, error)));
        if (report.valid()) {
            logger.info("Carte '%s' (%s) prête pour la rotation".formatted(label, mapId));
        } else {
            logger.error("Carte '%s' (%s) désactivée : validation échouée".formatted(label, mapId));
        }
    }
}
