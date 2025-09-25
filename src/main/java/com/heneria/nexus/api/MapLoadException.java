package com.heneria.nexus.api;

/**
 * Exception thrown when the catalog cannot be loaded.
 */
public final class MapLoadException extends Exception {

    /**
     * Creates a new exception describing a catalog loading failure.
     *
     * @param message human readable description of the issue
     */
    public MapLoadException(String message) {
        super(message);
    }

    /**
     * Creates a new exception describing a catalog loading failure with a cause.
     *
     * @param message human readable description of the issue
     * @param cause underlying cause for the failure
     */
    public MapLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
