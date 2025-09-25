package com.heneria.nexus.api;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable cache of MiniMessage templates resolved at load time.
 */
public interface ComponentTemplateCache {

    /**
     * Returns the prefix template shared across all messages.
     *
     * @return prefix template applied to chat messages
     */
    MiniMessageTemplate prefix();

    /**
     * Returns the template associated with the provided key if present.
     *
     * @param key identifier of the template to resolve
     * @return optional containing the template when available
     */
    Optional<MiniMessageTemplate> find(String key);

    /**
     * Returns the template associated with the provided key or throws if
     * missing. Implementations should surface the key in the thrown exception
     * to ease troubleshooting missing translations.
     *
     * @param key identifier of the template to resolve
     * @return template registered under the provided key
     * @throws IllegalArgumentException when no template is registered for the key
     */
    MiniMessageTemplate require(String key);

    /**
     * Returns an immutable snapshot of the cached templates.
     *
     * @return mapping between template keys and their definitions
     */
    Map<String, MiniMessageTemplate> templates();
}
