package com.heneria.nexus.api;

import com.heneria.nexus.service.LifecycleAware;
import java.util.Collection;
import java.util.Optional;

/**
 * Catalog of available arenas.
 */
public interface MapService extends LifecycleAware {

    /**
     * Loads the map catalog. Called during the {@code initialize} phase.
     *
     * @throws MapLoadException if the catalog cannot be read
     */
    void loadCatalog() throws MapLoadException;

    /**
     * Reloads the map catalog atomically from disk.
     *
     * @throws MapLoadException if the catalog cannot be read
     */
    void reload() throws MapLoadException;

    /**
     * Retrieves a map definition by identifier.
     *
     * @param id identifier of the map
     * @return optional containing the map definition when available
     */
    Optional<MapDefinition> getMap(String id);

    /**
     * Lists map definitions matching the provided query.
     *
     * @param query filtering query describing desired maps
     * @return immutable collection of matching definitions
     */
    Collection<MapDefinition> list(MapQuery query);

    /**
     * Performs expensive validations on the provided map identifier. Should be
     * invoked from the compute executor.
     *
     * @param mapId identifier of the map to validate
     * @return validation report describing detected issues
     */
    ValidationReport validate(String mapId);
}
