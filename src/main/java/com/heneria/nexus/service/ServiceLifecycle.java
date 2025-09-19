package com.heneria.nexus.service;

/**
 * Lifecycle states exposed for diagnostics.
 */
public enum ServiceLifecycle {
    NEW,
    INITIALIZING,
    INITIALIZED,
    STARTING,
    STARTED,
    STOPPING,
    STOPPED,
    FAILED
}
