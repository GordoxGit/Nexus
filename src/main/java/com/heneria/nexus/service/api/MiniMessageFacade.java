package com.heneria.nexus.service.api;

import com.heneria.nexus.service.LifecycleAware;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * High level facade encapsulating all MiniMessage rendering logic.
 */
public interface MiniMessageFacade extends LifecycleAware {

    /** Returns the cached prefix component. */
    Component prefix();

    /** Returns a rendered component for the provided key. */
    Component msg(String key, TagResolver... resolvers);

    /** Sends a rendered component directly to the provided audience. */
    void send(Audience target, String key, TagResolver... resolvers);

    /**
     * Sends a rendered component to multiple audiences. Implementations must
     * operate on the main server thread.
     */
    void broadcast(Iterable<? extends Audience> targets, String key, TagResolver... resolvers);

    /** Sends an action bar message. */
    void actionBar(Audience target, String key, TagResolver... resolvers);

    /**
     * Sends a title using the provided MiniMessage keys and timing profile.
     */
    void title(Audience target, String keyTitle, String keySubtitle, TimesProfile times, TagResolver... resolvers);

    /**
     * Attaches a boss bar to the target and returns a handle to control it.
     */
    BossBarHandle bossbar(Audience target, String barKey, BossBarProfile profile, TagResolver... resolvers);

    /** Returns the cache holding component templates. */
    ComponentTemplateCache componentTemplates();

    /** Returns the cache holding title templates. */
    TitleTemplateCache titleTemplates();

    /** Returns the registry of boss bar prototypes. */
    BossBarPrototypeRegistry bossBarPrototypes();

    /** Returns the resolver factory used to compose tag resolvers. */
    TagResolverFactory resolvers();
}
