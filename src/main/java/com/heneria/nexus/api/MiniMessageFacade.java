package com.heneria.nexus.api;

import com.heneria.nexus.service.LifecycleAware;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * High level facade encapsulating all MiniMessage rendering logic.
 */
public interface MiniMessageFacade extends LifecycleAware {

    /**
     * Returns the cached prefix component.
     *
     * @return component appended in front of most chat messages
     */
    Component prefix();

    /**
     * Returns a rendered component for the provided key.
     *
     * @param key message key to resolve
     * @param resolvers additional MiniMessage resolvers applied during rendering
     * @return rendered component
     */
    Component msg(String key, TagResolver... resolvers);

    /**
     * Sends a rendered component directly to the provided audience.
     *
     * @param target audience receiving the message
     * @param key message key to resolve
     * @param resolvers additional MiniMessage resolvers applied during rendering
     */
    void send(Audience target, String key, TagResolver... resolvers);

    /**
     * Sends a rendered component to multiple audiences. Implementations must
     * operate on the main server thread.
     *
     * @param targets audiences receiving the message
     * @param key message key to resolve
     * @param resolvers additional MiniMessage resolvers applied during rendering
     */
    void broadcast(Iterable<? extends Audience> targets, String key, TagResolver... resolvers);

    /**
     * Sends an action bar message.
     *
     * @param target audience receiving the action bar
     * @param key message key to resolve
     * @param resolvers additional MiniMessage resolvers applied during rendering
     */
    void actionBar(Audience target, String key, TagResolver... resolvers);

    /**
     * Sends a title using the provided MiniMessage keys and timing profile.
     *
     * @param target audience receiving the title
     * @param keyTitle message key for the title component
     * @param keySubtitle message key for the subtitle component
     * @param times timing profile applied to the title
     * @param resolvers additional MiniMessage resolvers applied during rendering
     */
    void title(Audience target, String keyTitle, String keySubtitle, TimesProfile times, TagResolver... resolvers);

    /**
     * Attaches a boss bar to the target and returns a handle to control it.
     *
     * @param target audience receiving the boss bar
     * @param barKey key identifying the boss bar prototype
     * @param profile profile describing how the boss bar should behave
     * @param resolvers additional MiniMessage resolvers applied during rendering
     * @return handle controlling the instantiated boss bar
     */
    BossBarHandle bossbar(Audience target, String barKey, BossBarProfile profile, TagResolver... resolvers);

    /**
     * Returns the cache holding component templates.
     *
     * @return component template cache used by the facade
     */
    ComponentTemplateCache componentTemplates();

    /**
     * Returns the cache holding title templates.
     *
     * @return title template cache used by the facade
     */
    TitleTemplateCache titleTemplates();

    /**
     * Returns the registry of boss bar prototypes.
     *
     * @return registry exposing available boss bar prototypes
     */
    BossBarPrototypeRegistry bossBarPrototypes();

    /**
     * Returns the resolver factory used to compose tag resolvers.
     *
     * @return resolver factory bundled with the facade
     */
    TagResolverFactory resolvers();
}
