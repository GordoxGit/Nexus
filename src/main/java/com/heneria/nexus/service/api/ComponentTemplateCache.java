package com.heneria.nexus.service.api;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable cache of MiniMessage templates resolved at load time.
 */
public interface ComponentTemplateCache {

    /**
     * Returns the prefix template shared across all messages.
     */
    MiniMessageTemplate prefix();

    /**
     * Returns the template associated with the provided key if present.
     */
    Optional<MiniMessageTemplate> find(String key);

    /**
     * Returns the template associated with the provided key or throws if
     * missing. Implementations should surface the key in the thrown exception
     * to ease troubleshooting missing translations.
     */
    MiniMessageTemplate require(String key);

    /**
     * Returns an immutable snapshot of the cached templates.
     */
    Map<String, MiniMessageTemplate> templates();
}
