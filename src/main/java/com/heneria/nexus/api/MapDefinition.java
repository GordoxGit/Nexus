package com.heneria.nexus.api;

import com.heneria.nexus.api.map.MapBlueprint;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an entry of the map catalog.
 *
 * @param id unique identifier of the map
 * @param displayName human readable map name
 * @param folder root folder containing the map resources
 * @param metadata additional metadata stored alongside the map
 * @param blueprint structured configuration extracted from {@code map.yml}
 */
public record MapDefinition(String id,
                            String displayName,
                            Path folder,
                            Map<String, Object> metadata,
                            MapBlueprint blueprint) {

    /**
     * Validates constructor arguments.
     */
    public MapDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(blueprint, "blueprint");
    }
}
