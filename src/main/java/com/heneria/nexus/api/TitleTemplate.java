package com.heneria.nexus.api;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;

/**
 * Represents a pre-built title template coupled to a timing profile.
 */
public interface TitleTemplate {

    /**
     * Returns the message key used for the title component.
     *
     * @return message key for the title text
     */
    String titleKey();

    /**
     * Returns the message key used for the subtitle component.
     *
     * @return message key for the subtitle text
     */
    String subtitleKey();

    /**
     * Returns the timing profile applied to the title.
     *
     * @return timing profile describing fade in/out timings
     */
    TimesProfile timesProfile();

    /**
     * Returns the cached title instance without additional placeholder
     * resolution.
     *
     * @return cached Adventure title
     */
    Title title();

    /**
     * Renders a title instance using the provided resolvers. Implementations
     * must re-use the cached timings and avoid MiniMessage parsing on the hot
     * path.
     *
     * @param resolvers additional MiniMessage resolvers applied during rendering
     * @return rendered Adventure title
     */
    Title render(TagResolver... resolvers);
}
