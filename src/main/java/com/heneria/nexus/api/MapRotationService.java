package com.heneria.nexus.api;

import com.heneria.nexus.service.LifecycleAware;
import java.util.List;

/**
 * Provides intelligent map rotation for new matches.
 */
public interface MapRotationService extends LifecycleAware {

    /**
     * Returns a list of map candidates that satisfy the provided context.
     *
     * @param context constraints describing the desired maps
     * @return ordered list of map definitions, possibly empty when no map matches
     */
    List<MapDefinition> getMapChoices(MapSelectionContext context);

    /**
     * Records the map that has been effectively selected for a match so it can
     * be excluded from the next rotation suggestions.
     *
     * @param mapId identifier of the map that was played
     */
    void recordMatch(String mapId);
}
