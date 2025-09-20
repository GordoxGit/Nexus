package com.heneria.nexus.service.api;

/**
 * Domain level exception for economy operations.
 */
public final class EconomyException extends Exception {

    public EconomyException(String message) {
        super(message);
    }

    public EconomyException(String message, Throwable cause) {
        super(message, cause);
    }
}
