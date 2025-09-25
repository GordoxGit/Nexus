package com.heneria.nexus.api;

/**
 * Domain level exception for economy operations.
 */
public final class EconomyException extends Exception {

    /**
     * Creates a new exception describing an economy failure.
     *
     * @param message human readable description of the issue
     */
    public EconomyException(String message) {
        super(message);
    }

    /**
     * Creates a new exception describing an economy failure with a cause.
     *
     * @param message human readable description of the issue
     * @param cause underlying cause of the failure
     */
    public EconomyException(String message, Throwable cause) {
        super(message, cause);
    }
}
