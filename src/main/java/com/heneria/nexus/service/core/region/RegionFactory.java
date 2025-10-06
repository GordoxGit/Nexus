package com.heneria.nexus.service.core.region;

import com.heneria.nexus.api.region.Region;
import com.heneria.nexus.api.region.RegionFlag;
import com.heneria.nexus.api.map.MapBlueprint.MapRegion;
import com.heneria.nexus.util.NexusLogger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.util.BoundingBox;

/**
 * Utility responsible for converting blueprint declarations into runtime region
 * models.
 */
public final class RegionFactory {

    private RegionFactory() {
    }

    public static List<Region> fromBlueprint(String mapId,
                                             List<MapRegion> blueprint,
                                             NexusLogger logger) {
        if (blueprint == null || blueprint.isEmpty()) {
            return List.of();
        }
        List<Region> regions = new ArrayList<>();
        for (MapRegion candidate : blueprint) {
            if (candidate == null) {
                continue;
            }
            try {
                Optional<Region> region = buildRegion(candidate);
                region.ifPresent(regions::add);
            } catch (IllegalArgumentException exception) {
                if (logger != null) {
                    logger.warn("[map:%s] Région '%s' ignorée: %s".formatted(mapId, candidate.id(), exception.getMessage()));
                }
            }
        }
        return List.copyOf(regions);
    }

    public static Map<RegionFlag, Object> extractFlagDefaults(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        EnumMap<RegionFlag, Object> flags = new EnumMap<>(RegionFlag.class);
        collectFlags("", source, flags);
        return Map.copyOf(flags);
    }

    private static Optional<Region> buildRegion(MapRegion candidate) {
        Map<String, Object> properties = candidate.properties();
        BoundingBox bounds = resolveBounds(properties)
                .orElseThrow(() -> new IllegalArgumentException("limites manquantes ou invalides"));
        EnumMap<RegionFlag, Object> flags = new EnumMap<>(RegionFlag.class);
        Object flagSection = properties.get("flags");
        if (flagSection instanceof Map<?, ?> map) {
            collectFlags("", map, flags);
        }
        properties.forEach((key, value) -> RegionFlag.fromKey(key).ifPresent(flag -> flags.put(flag, value)));
        Region.Builder builder = Region.builder(candidate.id()).bounds(bounds);
        flags.forEach(builder::flag);
        return Optional.of(builder.build());
    }

    private static Optional<BoundingBox> resolveBounds(Map<String, Object> properties) {
        Object first = firstPresent(properties, "min", "pos1", "corner1", "from", "a", "start");
        Object second = firstPresent(properties, "max", "pos2", "corner2", "to", "b", "end");
        if (first == null || second == null) {
            Object bounds = properties.get("bounds");
            if (bounds instanceof List<?> list && list.size() >= 2) {
                first = list.get(0);
                second = list.get(1);
            }
        }
        Optional<Vector3> min = parseVector(first);
        Optional<Vector3> max = parseVector(second);
        if (min.isEmpty() || max.isEmpty()) {
            return Optional.empty();
        }
        Vector3 a = min.get();
        Vector3 b = max.get();
        double minX = Math.min(a.x(), b.x());
        double minY = Math.min(a.y(), b.y());
        double minZ = Math.min(a.z(), b.z());
        double maxX = Math.max(a.x(), b.x());
        double maxY = Math.max(a.y(), b.y());
        double maxZ = Math.max(a.z(), b.z());
        if (Double.isNaN(minX) || Double.isNaN(minY) || Double.isNaN(minZ)
                || Double.isNaN(maxX) || Double.isNaN(maxY) || Double.isNaN(maxZ)) {
            return Optional.empty();
        }
        return Optional.of(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static Object firstPresent(Map<String, Object> properties, String... keys) {
        for (String key : keys) {
            Object value = properties.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Optional<Vector3> parseVector(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Vector3 vector) {
            return Optional.of(vector);
        }
        if (value instanceof Map<?, ?> map) {
            return parseVectorFromMap(map);
        }
        if (value instanceof List<?> list) {
            return parseVectorFromList(list);
        }
        if (value instanceof String string) {
            return parseVectorFromString(string);
        }
        if (value instanceof Number number) {
            double coordinate = number.doubleValue();
            return Optional.of(new Vector3(coordinate, coordinate, coordinate));
        }
        return Optional.empty();
    }

    private static Optional<Vector3> parseVectorFromMap(Map<?, ?> map) {
        Object xValue = map.get("x");
        Object yValue = map.get("y");
        Object zValue = map.get("z");
        if (xValue == null && yValue == null && zValue == null) {
            Object nested = map.get("pos");
            if (nested != null) {
                return parseVector(nested);
            }
            if (map.size() >= 3) {
                Object[] values = map.values().toArray();
                return parseVectorFromArray(values);
            }
            return Optional.empty();
        }
        Double x = parseCoordinate(xValue);
        Double y = parseCoordinate(yValue);
        Double z = parseCoordinate(zValue);
        if (x == null || y == null || z == null) {
            return Optional.empty();
        }
        return Optional.of(new Vector3(x, y, z));
    }

    private static Optional<Vector3> parseVectorFromList(List<?> list) {
        if (list.size() < 3) {
            return Optional.empty();
        }
        return parseVectorFromArray(list.toArray());
    }

    private static Optional<Vector3> parseVectorFromArray(Object[] values) {
        Double x = parseCoordinate(values[0]);
        Double y = parseCoordinate(values[1]);
        Double z = parseCoordinate(values[2]);
        if (x == null || y == null || z == null) {
            return Optional.empty();
        }
        return Optional.of(new Vector3(x, y, z));
    }

    private static Optional<Vector3> parseVectorFromString(String value) {
        String[] parts = value.split(",");
        if (parts.length < 3) {
            return Optional.empty();
        }
        Double x = parseCoordinate(parts[0]);
        Double y = parseCoordinate(parts[1]);
        Double z = parseCoordinate(parts[2]);
        if (x == null || y == null || z == null) {
            return Optional.empty();
        }
        return Optional.of(new Vector3(x, y, z));
    }

    private static Double parseCoordinate(Object value) {
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

    private static void collectFlags(String prefix, Object value, EnumMap<RegionFlag, Object> result) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, entry) -> {
                String path = join(prefix, String.valueOf(key));
                collectFlags(path, entry, result);
            });
            return;
        }
        if (value instanceof List<?> list) {
            for (Object element : list) {
                collectFlags(prefix, element, result);
            }
            return;
        }
        RegionFlag.fromKey(prefix).ifPresent(flag -> result.put(flag, value));
    }

    private static String join(String prefix, String key) {
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (prefix == null || prefix.isEmpty()) {
            return normalizedKey;
        }
        return prefix + "." + normalizedKey;
    }

    private record Vector3(double x, double y, double z) {
    }
}
