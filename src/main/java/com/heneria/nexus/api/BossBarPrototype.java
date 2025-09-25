package com.heneria.nexus.api;

import java.util.Set;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Immutable description of a boss bar resolved from configuration.
 */
public interface BossBarPrototype {

    /**
     * Unique message key associated with the bar name.
     *
     * @return translation key used to resolve the bar title
     */
    String key();

    /**
     * Returns the MiniMessage template for the bar name.
     *
     * @return MiniMessage template for the bar title
     */
    MiniMessageTemplate nameTemplate();

    /**
     * Returns the colour to use when instantiating the bar.
     *
     * @return Adventure colour applied to the bar
     */
    BossBar.Color color();

    /**
     * Returns the overlay to use when instantiating the bar.
     *
     * @return overlay style applied to the bar
     */
    BossBar.Overlay overlay();

    /**
     * Returns the static flags applied to the bar.
     *
     * @return immutable set of Adventure flags
     */
    Set<BossBar.Flag> flags();

    /**
     * Returns the cloning policy derived from the profile.
     *
     * @return clone policy describing how handles should be shared
     */
    BossBarProfile.ClonePolicy clonePolicy();

    /**
     * Creates a fresh Adventure boss bar instance based on the prototype.
     * Implementations must clone state rather than returning shared instances
     * when the clone policy requires isolation.
     *
     * @param resolvers additional MiniMessage resolvers applied to the title
     * @return newly created Adventure boss bar
     */
    BossBar instantiate(TagResolver... resolvers);
}
