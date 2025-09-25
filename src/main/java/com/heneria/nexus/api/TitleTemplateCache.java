package com.heneria.nexus.api;

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
     *
     * @param titleKey message key used for the title component
     * @param subtitleKey message key used for the subtitle component
     * @param profile timing profile applied to the title
     * @return optional containing the template when available
     */
    Optional<TitleTemplate> find(String titleKey, String subtitleKey, TimesProfile profile);

    /**
     * Returns the title template matching the provided keys and profile or
     * throws when absent.
     *
     * @param titleKey message key used for the title component
     * @param subtitleKey message key used for the subtitle component
     * @param profile timing profile applied to the title
     * @return title template registered under the provided keys
     * @throws IllegalArgumentException when no template matches the inputs
     */
    TitleTemplate require(String titleKey, String subtitleKey, TimesProfile profile);

    /**
     * Returns all registered title templates.
     *
     * @return immutable collection of title templates
     */
    Collection<TitleTemplate> templates();
}
