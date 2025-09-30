package com.heneria.nexus.service.core;

import com.heneria.nexus.api.ArenaMode;
import com.heneria.nexus.api.MapDefinition;
import com.heneria.nexus.api.MapQuery;
import com.heneria.nexus.api.MapRotationService;
import com.heneria.nexus.api.MapSelectionContext;
import com.heneria.nexus.api.MapService;
import com.heneria.nexus.api.map.MapBlueprint;
import com.heneria.nexus.api.map.MapBlueprint.MapRules;
import com.heneria.nexus.config.ConfigManager;
import com.heneria.nexus.config.MapsCatalogConfig;
import com.heneria.nexus.util.NexusLogger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Default in-memory implementation of {@link MapRotationService}.
 */
public final class MapRotationServiceImpl implements MapRotationService {

    private static final int MAX_HISTORY = 5;
    private static final int DEFAULT_CHOICES = 3;

    private final MapService mapService;
    private final ConfigManager configManager;
    private final NexusLogger logger;
    private final AtomicReference<List<MapDefinition>> pool = new AtomicReference<>(List.of());
    private final AtomicReference<MapsCatalogConfig.RotationSettings> rotationSettings =
            new AtomicReference<>(new MapsCatalogConfig.RotationSettings(true, true, true, 0));
    private final AtomicLong knownConfigVersion = new AtomicLong(-1L);
    private final Deque<String> recentHistory = new ArrayDeque<>();
    private final Object catalogLock = new Object();
    private final Object historyLock = new Object();

    public MapRotationServiceImpl(MapService mapService,
                                   ConfigManager configManager,
                                   NexusLogger logger) {
        this.mapService = Objects.requireNonNull(mapService, "mapService");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public CompletableFuture<Void> initialize() {
        refreshCatalog();
        return MapRotationService.super.initialize();
    }

    @Override
    public List<MapDefinition> getMapChoices(MapSelectionContext context) {
        Objects.requireNonNull(context, "context");
        refreshCatalogIfNeeded();
        List<MapDefinition> available = pool.get();
        if (available.isEmpty()) {
            return List.of();
        }
        List<MapDefinition> eligible = available.stream()
                .filter(definition -> supportsMode(definition, context.mode(), context.playerCount()))
                .filter(definition -> supportsPlayerCount(definition, context.playerCount()))
                .toList();
        if (eligible.isEmpty()) {
            return List.of();
        }
        Set<String> exclusions = new HashSet<>();
        for (String id : context.recentlyPlayed()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            exclusions.add(id.toLowerCase(Locale.ROOT));
        }
        synchronized (historyLock) {
            exclusions.addAll(recentHistory);
        }
        List<MapDefinition> nonRepeating = eligible.stream()
                .filter(definition -> !exclusions.contains(definition.id().toLowerCase(Locale.ROOT)))
                .toList();
        List<MapDefinition> candidates = nonRepeating.isEmpty() ? eligible : nonRepeating;
        MapsCatalogConfig.RotationSettings settings = rotationSettings.get();
        List<MapDefinition> selection = selectCandidates(candidates, settings);
        if (selection.isEmpty() && !candidates.isEmpty()) {
            logger.debug(() -> "Rotation des cartes: aucun choix final malgré des candidats disponibles");
        }
        return selection;
    }

    @Override
    public void recordMatch(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return;
        }
        String normalized = mapId.toLowerCase(Locale.ROOT);
        synchronized (historyLock) {
            recentHistory.remove(normalized);
            recentHistory.addFirst(normalized);
            while (recentHistory.size() > MAX_HISTORY) {
                recentHistory.removeLast();
            }
        }
    }

    private void refreshCatalogIfNeeded() {
        long version = configManager.version();
        if (version == knownConfigVersion.get() && !pool.get().isEmpty()) {
            return;
        }
        synchronized (catalogLock) {
            if (version != knownConfigVersion.get() || pool.get().isEmpty()) {
                refreshCatalog();
            }
        }
    }

    private void refreshCatalog() {
        Collection<MapDefinition> definitions = mapService.list(MapQuery.any());
        pool.set(List.copyOf(definitions));
        MapsCatalogConfig mapsConfig = configManager.getMaps();
        rotationSettings.set(mapsConfig.rotation());
        knownConfigVersion.set(configManager.version());
        logger.debug(() -> "Pool de rotation mis à jour: " + definitions.size() + " carte(s) valide(s)");
    }

    private boolean supportsMode(MapDefinition definition, ArenaMode mode, int playerCount) {
        if (mode == null) {
            return true;
        }
        Object rawModes = definition.metadata().get("modes");
        if (!(rawModes instanceof Collection<?> collection) || collection.isEmpty()) {
            return true;
        }
        Set<String> supported = collection.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        if (supported.isEmpty()) {
            return true;
        }
        String modeTag = mode.name().toUpperCase(Locale.ROOT);
        if (supported.contains(modeTag)) {
            return true;
        }
        Optional<String> formatTag = formatTagForPlayers(playerCount);
        if (formatTag.isPresent() && supported.contains(formatTag.get())) {
            return true;
        }
        return false;
    }

    private Optional<String> formatTagForPlayers(int playerCount) {
        if (playerCount <= 0 || playerCount % 2 != 0) {
            return Optional.empty();
        }
        int perTeam = playerCount / 2;
        if (perTeam <= 0) {
            return Optional.empty();
        }
        return Optional.of((perTeam + "V" + perTeam).toUpperCase(Locale.ROOT));
    }

    private boolean supportsPlayerCount(MapDefinition definition, int playerCount) {
        if (playerCount <= 0) {
            return true;
        }
        MapBlueprint blueprint = definition.blueprint();
        if (blueprint == null) {
            return true;
        }
        MapRules rules = blueprint.rules();
        if (rules == null) {
            return true;
        }
        Integer min = rules.minPlayers();
        if (min != null && playerCount < min) {
            return false;
        }
        Integer max = rules.maxPlayers();
        if (max != null && playerCount > max) {
            return false;
        }
        return true;
    }

    private List<MapDefinition> selectCandidates(List<MapDefinition> candidates, MapsCatalogConfig.RotationSettings settings) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(DEFAULT_CHOICES, candidates.size());
        if (!settings.enabled()) {
            return candidates.stream()
                    .sorted(Comparator.comparing(MapDefinition::id))
                    .limit(limit)
                    .toList();
        }
        if (!settings.weightedPick()) {
            List<MapDefinition> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());
            return List.copyOf(shuffled.subList(0, limit));
        }
        List<MapDefinition> poolCopy = new ArrayList<>(candidates);
        List<MapDefinition> result = new ArrayList<>(limit);
        for (int i = 0; i < limit && !poolCopy.isEmpty(); i++) {
            MapDefinition picked = pickWeighted(poolCopy);
            if (picked == null) {
                break;
            }
            result.add(picked);
            poolCopy.remove(picked);
        }
        return List.copyOf(result);
    }

    private MapDefinition pickWeighted(List<MapDefinition> candidates) {
        int totalWeight = 0;
        List<Integer> weights = new ArrayList<>(candidates.size());
        for (MapDefinition candidate : candidates) {
            int weight = Math.max(1, weightOf(candidate));
            weights.add(weight);
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            return candidates.get(0);
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private int weightOf(MapDefinition definition) {
        Object weight = definition.metadata().get("weight");
        if (weight instanceof Number number) {
            return number.intValue();
        }
        if (weight instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }
}
