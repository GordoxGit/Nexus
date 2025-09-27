package com.heneria.nexus.admin;

/**
 * Signals that an import payload is invalid or unsafe to apply.
 */
public final class PlayerDataValidationException extends RuntimeException {

    public PlayerDataValidationException(String message) {
        super(message);
    }
}
