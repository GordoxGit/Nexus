package com.heneria.nexus.api;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry exposing boss bar prototypes resolved from configuration.
 */
public interface BossBarPrototypeRegistry {

    /**
     * Returns the prototype matching the provided key if present.
     *
     * @param key identifier of the prototype to resolve
     * @return optional containing the prototype when available
     */
    Optional<BossBarPrototype> find(String key);

    /**
     * Returns the prototype matching the provided key or throws when absent.
     *
     * @param key identifier of the prototype to resolve
     * @return prototype registered under the provided key
     * @throws IllegalArgumentException when no prototype exists for the key
     */
    BossBarPrototype require(String key);

    /**
     * Returns all known prototypes.
     *
     * @return immutable view of the registered prototypes
     */
    Collection<BossBarPrototype> prototypes();
}
