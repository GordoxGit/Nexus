package com.heneria.nexus.service.api;

/**
 * Exception raised when an arena instance cannot be created.
 */
public final class ArenaCreationException extends Exception {

    public ArenaCreationException(String message) {
        super(message);
    }

    public ArenaCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
