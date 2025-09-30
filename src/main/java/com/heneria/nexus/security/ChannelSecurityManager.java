package com.heneria.nexus.security;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.util.NexusLogger;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Centralises validation of cross-server communication channels.
 */
public final class ChannelSecurityManager {

    private final NexusLogger logger;
    private final AtomicReference<Set<String>> allowedChannels = new AtomicReference<>(Set.of());

    public ChannelSecurityManager(NexusLogger logger, CoreConfig coreConfig) {
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(coreConfig, "coreConfig");
        applySettings(coreConfig.securitySettings());
    }

    /**
     * Updates the authorised channel list using the freshly loaded configuration.
     */
    public void applySettings(CoreConfig.SecuritySettings securitySettings) {
        Objects.requireNonNull(securitySettings, "securitySettings");
        Set<String> updated = normalize(securitySettings.allowedChannels());
        allowedChannels.set(updated);
        if (updated.isEmpty()) {
            logger.warn("Aucun canal autorisé n'est configuré. Toutes les communications inter-serveurs seront bloquées.");
        } else {
            logger.debug(() -> "Canaux autorisés mis à jour: " + updated);
        }
    }

    /**
     * Validates whether a channel is currently authorised.
     */
    public boolean isChannelAllowed(String channelName) {
        if (channelName == null || channelName.isBlank()) {
            return false;
        }
        String normalized = normalize(channelName);
        return allowedChannels.get().contains(normalized);
    }

    public Set<String> allowedChannels() {
        return allowedChannels.get();
    }

    private Set<String> normalize(Set<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = channels.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(channel -> !channel.isEmpty())
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(normalized);
    }

    private String normalize(String channel) {
        return channel.toLowerCase(Locale.ROOT);
    }
}

