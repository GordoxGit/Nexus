package com.heneria.nexus.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Represents a pre-parsed MiniMessage template that can render lightweight
 * components without re-parsing the underlying string at runtime.
 */
public interface MiniMessageTemplate {

    /**
     * Returns the unique message key associated with this template.
     *
     * @return message key identifying the template
     */
    String key();

    /**
     * Renders the template using the provided tag resolvers.
     * Implementations must not perform a MiniMessage parse on the hot path.
     *
     * @param resolvers additional MiniMessage resolvers applied during rendering
     * @return rendered component
     */
    Component render(TagResolver... resolvers);

    /**
     * Returns the pre-parsed component associated with the template without
     * applying additional resolvers.
     *
     * @return cached component for the template
     */
    Component component();

    /**
     * Returns the raw MiniMessage source string, useful for logging and
     * debugging reload issues.
     *
     * @return raw MiniMessage source backing the template
     */
    String source();
}
