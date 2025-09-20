package com.heneria.nexus.service.api;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;

/**
 * Represents a pre-built title template coupled to a timing profile.
 */
public interface TitleTemplate {

    /**
     * Returns the message key used for the title component.
     */
    String titleKey();

    /**
     * Returns the message key used for the subtitle component.
     */
    String subtitleKey();

    /**
     * Returns the timing profile applied to the title.
     */
    TimesProfile timesProfile();

    /**
     * Returns the cached title instance without additional placeholder
     * resolution.
     */
    Title title();

    /**
     * Renders a title instance using the provided resolvers. Implementations
     * must re-use the cached timings and avoid MiniMessage parsing on the hot
     * path.
     */
    Title render(TagResolver... resolvers);
}
