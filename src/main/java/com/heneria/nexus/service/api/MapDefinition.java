package com.heneria.nexus.service.api;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an entry of the map catalog.
 */
public record MapDefinition(String id, String displayName, Path folder, Map<String, Object> metadata) {

    public MapDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(metadata, "metadata");
    }
}
