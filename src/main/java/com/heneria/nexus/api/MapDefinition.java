package com.heneria.nexus.api;

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
 */
public record MapDefinition(String id, String displayName, Path folder, Map<String, Object> metadata) {

    /**
     * Validates constructor arguments.
     */
    public MapDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(metadata, "metadata");
    }
}
