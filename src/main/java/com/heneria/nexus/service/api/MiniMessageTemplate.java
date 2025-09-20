package com.heneria.nexus.service.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Represents a pre-parsed MiniMessage template that can render lightweight
 * components without re-parsing the underlying string at runtime.
 */
public interface MiniMessageTemplate {

    /**
     * Returns the unique message key associated with this template.
     */
    String key();

    /**
     * Renders the template using the provided tag resolvers.
     * Implementations must not perform a MiniMessage parse on the hot path.
     */
    Component render(TagResolver... resolvers);

    /**
     * Returns the pre-parsed component associated with the template without
     * applying additional resolvers.
     */
    Component component();

    /**
     * Returns the raw MiniMessage source string, useful for logging and
     * debugging reload issues.
     */
    String source();
}
