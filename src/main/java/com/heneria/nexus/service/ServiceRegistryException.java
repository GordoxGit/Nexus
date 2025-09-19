package com.heneria.nexus.service;

/**
 * Exception thrown when the service registry cannot complete an operation.
 */
public final class ServiceRegistryException extends RuntimeException {

    public ServiceRegistryException(String message) {
        super(message);
    }

    public ServiceRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
