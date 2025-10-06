package com.heneria.nexus.api.map;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured representation of the {@code map.yml} file associated with a map.
 */
public record MapBlueprint(boolean configurationPresent,
                           MapAsset asset,
                           MapRules rules,
                           List<MapTeam> teams,
                           List<MapRegion> regions,
                           List<MapInteractive> interactives,
                           Map<String, Object> extras) {

    /**
     * Creates an empty blueprint signalling that {@code map.yml} is missing.
     *
     * @return blueprint without configuration content
     */
    public static MapBlueprint missingConfiguration() {
        return new MapBlueprint(false, null, null, List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Validates constructor arguments.
     */
    public MapBlueprint {
        Objects.requireNonNull(teams, "teams");
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(interactives, "interactives");
        Objects.requireNonNull(extras, "extras");
    }

    /**
     * Description of the schematic/world asset used by the map.
     */
    public record MapAsset(String type, String file, Map<String, Object> properties) {

        public MapAsset {
            Objects.requireNonNull(properties, "properties");
            properties = Map.copyOf(properties);
        }

        public MapAsset(String type, String file) {
            this(type, file, Map.of());
        }

        public MapAsset(String file, Map<String, Object> properties) {
            this(null, file, properties);
        }

        public MapAsset(String file) {
            this(null, file, Map.of());
        }
    }

    /**
     * Global gameplay rules of the map.
     */
    public record MapRules(Integer minPlayers, Integer maxPlayers, Map<String, Object> properties) {

        public MapRules {
            Objects.requireNonNull(properties, "properties");
            properties = Map.copyOf(properties);
        }
    }

    /**
     * Configuration of a team participating on the map.
     */
    public record MapTeam(String id,
                          String displayName,
                          MapVector spawn,
                          MapNexus nexus,
                          Map<String, Object> properties) {

        public MapTeam {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(properties, "properties");
            properties = Map.copyOf(properties);
        }
    }

    /**
     * Configuration of the nexus associated with a team.
     */
    public record MapNexus(MapVector position, Integer hitPoints, Integer radius, Map<String, Object> properties) {

        public MapNexus {
            Objects.requireNonNull(properties, "properties");
            properties = Map.copyOf(properties);
        }
    }

    /**
     * Utility record used for coordinates in the blueprint.
     */
    public record MapVector(Double x, Double y, Double z, Float yaw, Float pitch) {

        /**
         * Indicates whether the vector provides valid XYZ coordinates.
         *
         * @return {@code true} when x, y and z are all provided
         */
        public boolean hasCoordinates() {
            return x != null && y != null && z != null;
        }
    }

    /**
     * Declaration of a named region.
     */
    public record MapRegion(String id, Map<String, Object> properties) {

        public MapRegion {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(properties, "properties");
            properties = Map.copyOf(properties);
        }
    }

    /**
     * Definition of an interactive object defined in {@code map.yml}.
     */
    public record MapInteractive(String id, Map<String, Object> properties) {

        public MapInteractive {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(properties, "properties");
            properties = Map.copyOf(properties);
        }
    }
}
