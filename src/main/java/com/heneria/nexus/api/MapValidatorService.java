package com.heneria.nexus.api;

import com.heneria.nexus.api.map.MapBlueprint;

/**
 * Service responsible for validating loaded maps.
 */
public interface MapValidatorService {

    /**
     * Validates the provided map definition and returns a report describing
     * potential issues.
     *
     * @param definition high level definition of the map
     * @param blueprint  structured configuration extracted from {@code map.yml}
     * @return validation report describing warnings and blocking errors
     */
    ValidationReport validate(MapDefinition definition, MapBlueprint blueprint);
}
