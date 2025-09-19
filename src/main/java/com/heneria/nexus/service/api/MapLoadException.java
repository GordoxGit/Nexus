package com.heneria.nexus.service.api;

/**
 * Exception thrown when the catalog cannot be loaded.
 */
public final class MapLoadException extends Exception {

    public MapLoadException(String message) {
        super(message);
    }

    public MapLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
