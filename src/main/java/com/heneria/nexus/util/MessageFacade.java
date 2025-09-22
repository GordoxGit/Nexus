package com.heneria.nexus.util;

import com.heneria.nexus.config.MessageBundle;
import com.heneria.nexus.config.MessageCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Provides MiniMessage parsing utilities with caching and locale support.
 */
public final class MessageFacade {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NexusLogger logger;
    private volatile boolean placeholderApiAvailable;
    private final AtomicBoolean placeholderFailureLogged = new AtomicBoolean();
    private volatile MessageCatalog catalog;

    public MessageFacade(MessageCatalog catalog, NexusLogger logger, boolean placeholderApiAvailable) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.placeholderApiAvailable = placeholderApiAvailable;
    }

    public void update(MessageCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public void updatePlaceholderAvailability(boolean available) {
        this.placeholderApiAvailable = available;
    }

    public Component render(String key, TagResolver... resolvers) {
        return render(catalog.fallbackLocale(), null, key, resolvers);
    }

    public Component render(CommandSender sender, String key, TagResolver... resolvers) {
        Objects.requireNonNull(sender, "sender");
        if (sender instanceof Player player) {
            return render(player, key, resolvers);
        }
        return render(resolveLocale(sender), null, key, resolvers);
    }

    public Component render(Player player, String key, TagResolver... resolvers) {
        Objects.requireNonNull(player, "player");
        return render(player.locale(), player, key, resolvers);
    }

    public Component render(Locale locale, String key, TagResolver... resolvers) {
        return render(locale, null, key, resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(render(sender, key, resolvers));
    }

    public Optional<Component> lookup(String key) {
        Objects.requireNonNull(key, "key");
        return catalog.fallback().entry(key).map(MessageBundle.MessageEntry::component);
    }

    public Component raw(String rawMessage, TagResolver... resolvers) {
        return deserialize(rawMessage, resolvers);
    }

    public Optional<Component> prefix() {
        return resolvePrefix(catalog.fallbackLocale(), null);
    }

    public Optional<Component> prefix(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (sender instanceof Player player) {
            return prefix(player);
        }
        return resolvePrefix(resolveLocale(sender), null);
    }

    public Optional<Component> prefix(Player player) {
        Objects.requireNonNull(player, "player");
        return resolvePrefix(player.locale(), player);
    }

    public Optional<List<Component>> messageList(String key) {
        return messageList(catalog.fallbackLocale(), null, key);
    }

    public Optional<List<Component>> messageList(CommandSender sender, String key, TagResolver... resolvers) {
        Objects.requireNonNull(sender, "sender");
        if (sender instanceof Player player) {
            return messageList(player, key, resolvers);
        }
        return messageList(resolveLocale(sender), null, key, resolvers);
    }

    public Optional<List<Component>> messageList(Player player, String key, TagResolver... resolvers) {
        Objects.requireNonNull(player, "player");
        return messageList(player.locale(), player, key, resolvers);
    }

    private Optional<Component> resolvePrefix(Locale locale, Player player) {
        MessageCatalog current = catalog;
        MessageBundle primary = current.resolve(locale);
        MessageBundle fallback = current.fallback();
        MessageBundle.MessageEntry entry = primary.prefixEntry().orElse(null);
        if (entry == null && fallback != primary) {
            entry = fallback.prefixEntry().orElse(null);
        }
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(renderEntry(entry, player));
    }

    private Optional<List<Component>> messageList(Locale locale, Player player, String key, TagResolver... resolvers) {
        MessageCatalog current = catalog;
        MessageBundle primary = current.resolve(locale);
        MessageBundle fallback = current.fallback();
        List<MessageBundle.MessageEntry> entries = primary.entryList(key).orElse(null);
        if (entries == null && fallback != primary) {
            entries = fallback.entryList(key).orElse(null);
        }
        if (entries == null) {
            return Optional.empty();
        }
        List<Component> components = new ArrayList<>(entries.size());
        for (MessageBundle.MessageEntry entry : entries) {
            components.add(renderEntry(entry, player, resolvers));
        }
        return Optional.of(List.copyOf(components));
    }

    private Component render(Locale locale, Player player, String key, TagResolver... resolvers) {
        MessageCatalog current = catalog;
        MessageBundle primary = current.resolve(locale);
        MessageBundle fallback = current.fallback();
        MessageBundle.MessageEntry entry = primary.entry(key).orElse(null);
        if (entry == null && fallback != primary) {
            entry = fallback.entry(key).orElse(null);
        }
        if (entry == null) {
            return missingMessage(key);
        }
        return renderEntry(entry, player, resolvers);
    }

    private Component renderEntry(MessageBundle.MessageEntry entry, Player player, TagResolver... resolvers) {
        String raw = entry.raw();
        String processed = applyPlaceholderApi(player, raw);
        if ((resolvers == null || resolvers.length == 0) && processed.equals(raw)) {
            return entry.component();
        }
        return deserialize(processed, resolvers);
    }

    private String applyPlaceholderApi(Player player, String input) {
        if (!placeholderApiAvailable) {
            return input;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, input);
        } catch (Throwable throwable) {
            if (placeholderFailureLogged.compareAndSet(false, true)) {
                logger.warn("Erreur lors de l'évaluation PlaceholderAPI", throwable);
            }
            return input;
        }
    }

    private Locale resolveLocale(CommandSender sender) {
        try {
            return sender.locale();
        } catch (Throwable ignored) {
            return null;
        }
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
