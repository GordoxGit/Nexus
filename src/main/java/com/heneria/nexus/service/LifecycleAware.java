package com.heneria.nexus.service;

import java.util.concurrent.CompletableFuture;

/**
 * Marks a service that exposes lifecycle hooks.
 */
public interface LifecycleAware {

    default CompletableFuture<Void> start() {
        return CompletableFuture.completedFuture(null);
    }

    default CompletableFuture<Void> stop() {
        return CompletableFuture.completedFuture(null);
    }
}
