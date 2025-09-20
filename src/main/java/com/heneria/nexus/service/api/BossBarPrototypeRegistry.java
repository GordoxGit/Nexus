package com.heneria.nexus.service.api;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry exposing boss bar prototypes resolved from configuration.
 */
public interface BossBarPrototypeRegistry {

    /** Returns the prototype matching the provided key if present. */
    Optional<BossBarPrototype> find(String key);

    /** Returns the prototype matching the provided key or throws when absent. */
    BossBarPrototype require(String key);

    /** Returns all known prototypes. */
    Collection<BossBarPrototype> prototypes();
}
