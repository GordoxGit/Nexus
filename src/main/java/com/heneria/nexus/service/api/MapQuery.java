package com.heneria.nexus.service.api;

import java.util.Optional;

/**
 * Simple filter used to list maps.
 */
public record MapQuery(Optional<ArenaMode> mode, Optional<String> regionTag) {

    public MapQuery {
        mode = mode == null ? Optional.empty() : mode;
        regionTag = regionTag == null ? Optional.empty() : regionTag;
    }

    public static MapQuery any() {
        return new MapQuery(Optional.empty(), Optional.empty());
    }
}
