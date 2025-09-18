package com.heneria.nexus.service;

import com.heneria.nexus.util.NexusLogger;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple registry able to manage service lifecycles.
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final NexusLogger logger;

    public ServiceRegistry(NexusLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public <T> void register(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        services.put(type, instance);
        if (instance instanceof LifecycleAware lifecycleAware) {
            lifecycleAware.start().exceptionally(throwable -> {
                logger.error("Erreur lors du démarrage du service " + type.getSimpleName(), throwable);
                return null;
            });
        }
    }

    public <T> Optional<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    public Map<Class<?>, Object> services() {
        return Collections.unmodifiableMap(services);
    }

    public CompletableFuture<Void> shutdown() {
        CompletableFuture<?>[] futures = services.entrySet().stream()
                .map(entry -> {
                    Object instance = entry.getValue();
                    if (instance instanceof LifecycleAware lifecycleAware) {
                        return lifecycleAware.stop().exceptionally(throwable -> {
                            logger.error("Erreur lors de l'arrêt du service " + entry.getKey().getSimpleName(), throwable);
                            return null;
                        });
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .toArray(CompletableFuture[]::new);
        services.clear();
        return CompletableFuture.allOf(futures);
    }
}
