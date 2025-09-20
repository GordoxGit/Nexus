package com.heneria.nexus.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Immutable diagnostics snapshot for a managed service.
 */
public record ServiceStateSnapshot(Class<?> serviceType,
                                   ServiceLifecycle lifecycle,
                                   boolean healthy,
                                   Optional<Throwable> lastError,
                                   Duration initializationDuration,
                                   Duration startDuration,
                                   Duration stopDuration,
                                   List<Class<?>> dependencies) {
}
