package com.heneria.nexus.service.api;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry exposing immutable title templates keyed by their message entries
 * and timing profile.
 */
public interface TitleTemplateCache {

    /**
     * Returns the title template matching the provided keys and profile if
     * available.
     */
    Optional<TitleTemplate> find(String titleKey, String subtitleKey, TimesProfile profile);

    /**
     * Returns the title template matching the provided keys and profile or
     * throws when absent.
     */
    TitleTemplate require(String titleKey, String subtitleKey, TimesProfile profile);

    /**
     * Returns all registered title templates.
     */
    Collection<TitleTemplate> templates();
}
