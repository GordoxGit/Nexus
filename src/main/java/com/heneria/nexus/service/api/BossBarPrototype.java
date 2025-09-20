package com.heneria.nexus.service.api;

import java.util.Set;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Immutable description of a boss bar resolved from configuration.
 */
public interface BossBarPrototype {

    /** Unique message key associated with the bar name. */
    String key();

    /** Returns the MiniMessage template for the bar name. */
    MiniMessageTemplate nameTemplate();

    /** Returns the colour to use when instantiating the bar. */
    BossBar.Color color();

    /** Returns the overlay to use when instantiating the bar. */
    BossBar.Overlay overlay();

    /** Returns the static flags applied to the bar. */
    Set<BossBar.Flag> flags();

    /** Returns the cloning policy derived from the profile. */
    BossBarProfile.ClonePolicy clonePolicy();

    /**
     * Creates a fresh Adventure boss bar instance based on the prototype.
     * Implementations must clone state rather than returning shared instances
     * when the clone policy requires isolation.
     */
    BossBar instantiate(TagResolver... resolvers);
}
