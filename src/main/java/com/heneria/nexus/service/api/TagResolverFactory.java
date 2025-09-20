package com.heneria.nexus.service.api;

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
     */
    TagResolver resolverStatic(Map<String, String> entries);

    /** Builds a resolver exposing player related placeholders. */
    TagResolver resolverPlayer(Player player);

    /** Builds a resolver exposing arena related placeholders. */
    TagResolver resolverArena(ArenaContext context);

    /** Returns a new builder for composing resolvers. */
    Builder builder();

    interface Builder {

        /** Adds an already built resolver to the composite. */
        Builder add(TagResolver resolver);

        /**
         * Adds a resolver supplier whose value will be cached for the duration
         * of the rendering pass to avoid repeated heavy computations.
         */
        Builder caching(Supplier<TagResolver> resolverSupplier);

        /** Builds the composite resolver. */
        TagResolver build();
    }
}
