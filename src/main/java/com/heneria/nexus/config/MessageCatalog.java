package com.heneria.nexus.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Holds all loaded message bundles and provides locale resolution helpers.
 */
public final class MessageCatalog {

    private final Map<String, MessageBundle> bundles;
    private final MessageBundle fallback;
    private final String fallbackKey;

    public MessageCatalog(Collection<MessageBundle> bundles, MessageBundle fallback) {
        Objects.requireNonNull(bundles, "bundles");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        Map<String, MessageBundle> normalized = new LinkedHashMap<>();
        for (MessageBundle bundle : bundles) {
            if (bundle == null) {
                continue;
            }
            register(normalized, bundle);
        }
        register(normalized, fallback);
        this.bundles = Collections.unmodifiableMap(normalized);
        this.fallbackKey = normalize(fallback.locale());
    }

    private void register(Map<String, MessageBundle> target, MessageBundle bundle) {
        String full = normalize(bundle.locale());
        target.put(full, bundle);
        String language = bundle.locale().getLanguage();
        if (!language.isEmpty()) {
            target.putIfAbsent(language.toLowerCase(Locale.ROOT), bundle);
        }
    }

    private String normalize(Locale locale) {
        return locale.toLanguageTag().toLowerCase(Locale.ROOT);
    }

    public MessageBundle fallback() {
        return fallback;
    }

    public Locale fallbackLocale() {
        return fallback.locale();
    }

    public MessageBundle resolve(Locale locale) {
        if (locale == null) {
            return fallback;
        }
        String languageTag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
        MessageBundle bundle = bundles.get(languageTag);
        if (bundle != null) {
            return bundle;
        }
        String language = locale.getLanguage();
        if (!language.isEmpty()) {
            bundle = bundles.get(language.toLowerCase(Locale.ROOT));
            if (bundle != null) {
                return bundle;
            }
        }
        bundle = bundles.get(fallbackKey);
        return bundle != null ? bundle : fallback;
    }

    public Map<String, MessageBundle> bundles() {
        return bundles;
    }
}
