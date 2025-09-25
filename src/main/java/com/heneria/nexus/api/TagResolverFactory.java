package com.heneria.nexus.api;

import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * Factory responsible for building MiniMessage {@link TagResolver} instances
 * used by the {@link MiniMessageFacade}.
 */
public interface TagResolverFactory {

    /**
     * Returns a resolver backed by static key/value entries typically coming
     * from the server configuration.
     *
     * @param entries mapping between placeholder keys and their raw values
     * @return resolver exposing the supplied entries
     */
    TagResolver resolverStatic(Map<String, String> entries);

    /**
     * Builds a resolver exposing player related placeholders.
     *
     * @param player player whose data should be exposed
     * @return resolver bound to the player
     */
    TagResolver resolverPlayer(Player player);

    /**
     * Builds a resolver exposing arena related placeholders.
     *
     * @param context arena context describing the current match
     * @return resolver bound to the arena context
     */
    TagResolver resolverArena(ArenaContext context);

    /**
     * Returns a new builder for composing resolvers.
     *
     * @return builder for composite resolvers
     */
    Builder builder();

    interface Builder {

        /**
         * Adds an already built resolver to the composite.
         *
         * @param resolver resolver to include in the composite
         * @return this builder for chaining
         */
        Builder add(TagResolver resolver);

        /**
         * Adds a resolver supplier whose value will be cached for the duration
         * of the rendering pass to avoid repeated heavy computations.
         *
         * @param resolverSupplier supplier providing a resolver lazily
         * @return this builder for chaining
         */
        Builder caching(Supplier<TagResolver> resolverSupplier);

        /**
         * Builds the composite resolver.
         *
         * @return composed resolver aggregating all entries
         */
        TagResolver build();
    }
}
