package com.heneria.nexus.service.api;

import com.heneria.nexus.service.LifecycleAware;
import java.util.Collection;
import java.util.Optional;

/**
 * Catalog of available arenas.
 */
public interface MapService extends LifecycleAware {

    /**
     * Loads the map catalog. Called during the {@code initialize} phase.
     */
    void loadCatalog() throws MapLoadException;

    /**
     * Reloads the map catalog atomically from disk.
     */
    void reload() throws MapLoadException;

    /**
     * Retrieves a map definition by identifier.
     */
    Optional<MapDefinition> getMap(String id);

    /**
     * Lists map definitions matching the provided query.
     */
    Collection<MapDefinition> list(MapQuery query);

    /**
     * Performs expensive validations on the provided map identifier. Should be
     * invoked from the compute executor.
     */
    ValidationReport validate(String mapId);
}
