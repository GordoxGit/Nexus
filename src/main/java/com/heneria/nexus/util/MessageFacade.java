package com.heneria.nexus.util;

import com.heneria.nexus.config.MessageBundle;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Provides MiniMessage parsing utilities with caching.
 */
public final class MessageFacade {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NexusLogger logger;
    private volatile MessageBundle bundle;
    private final Map<String, Component> cache = new ConcurrentHashMap<>();

    public MessageFacade(MessageBundle bundle, NexusLogger logger) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void update(MessageBundle bundle) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        cache.clear();
    }

    public Component render(String key, TagResolver... resolvers) {
        String template = lookup(key).orElse("<red>Message manquant: " + key + "</red>");
        if (resolvers.length == 0) {
            return cache.computeIfAbsent(template, this::deserialize);
        }
        return deserialize(template, resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(render(key, resolvers));
    }

    public Locale locale() {
        return bundle.locale();
    }

    public Optional<String> lookup(String key) {
        return bundle.message(key);
    }

    public Component raw(String rawMessage, TagResolver... resolvers) {
        return deserialize(rawMessage, resolvers);
    }

    private Component deserialize(String template) {
        try {
            return miniMessage.deserialize(template);
        } catch (Exception exception) {
            logger.warn("MiniMessage invalide: " + template, exception);
            return Component.text(template);
        }
    }

    private Component deserialize(String template, TagResolver... resolvers) {
        try {
            return miniMessage.deserialize(template, resolvers);
        } catch (Exception exception) {
            logger.warn("MiniMessage invalide: " + template, exception);
            return Component.text(template);
        }
    }
}
