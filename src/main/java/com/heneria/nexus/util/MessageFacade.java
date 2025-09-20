package com.heneria.nexus.util;

import com.heneria.nexus.config.MessageBundle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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

    public MessageFacade(MessageBundle bundle, NexusLogger logger) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void update(MessageBundle bundle) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
    }

    public Component render(String key, TagResolver... resolvers) {
        MessageBundle.MessageEntry entry = bundle.entry(key).orElse(null);
        if (entry == null) {
            return missingMessage(key);
        }
        if (resolvers.length == 0) {
            return entry.component();
        }
        return deserialize(entry.raw(), resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(render(key, resolvers));
    }

    public Locale locale() {
        return bundle.locale();
    }

    public Optional<Component> lookup(String key) {
        return bundle.message(key);
    }

    public Component raw(String rawMessage, TagResolver... resolvers) {
        return deserialize(rawMessage, resolvers);
    }

    public Optional<Component> prefix() {
        return bundle.prefix();
    }

    public Optional<List<Component>> messageList(String key) {
        return bundle.messageList(key);
    }

    private Component missingMessage(String key) {
        return Component.text("Message manquant: " + key);
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
