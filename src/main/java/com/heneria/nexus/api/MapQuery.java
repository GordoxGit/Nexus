package com.heneria.nexus.api;

import java.util.Optional;

/**
 * Simple filter used to list maps.
 *
 * @param mode optional gameplay mode restriction
 * @param regionTag optional region tag restriction
 */
public record MapQuery(Optional<ArenaMode> mode, Optional<String> regionTag) {

    /**
     * Normalises {@code null} values for optional fields.
     */
    public MapQuery {
        mode = mode == null ? Optional.empty() : mode;
        regionTag = regionTag == null ? Optional.empty() : regionTag;
    }

    /**
     * Creates a query without any filtering restrictions.
     *
     * @return query matching all maps
     */
    public static MapQuery any() {
        return new MapQuery(Optional.empty(), Optional.empty());
    }
}
