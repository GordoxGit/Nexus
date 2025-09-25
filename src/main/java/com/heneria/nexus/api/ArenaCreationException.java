package com.heneria.nexus.api;

/**
 * Exception raised when an arena instance cannot be created.
 */
public final class ArenaCreationException extends Exception {

    /**
     * Creates a new exception with the provided message.
     *
     * @param message human readable description of the failure
     */
    public ArenaCreationException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the provided message and cause.
     *
     * @param message human readable description of the failure
     * @param cause underlying cause for the creation failure
     */
    public ArenaCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
