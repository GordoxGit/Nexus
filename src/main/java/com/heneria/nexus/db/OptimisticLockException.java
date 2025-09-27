package com.heneria.nexus.db;

/**
 * Exception raised when an optimistic locking conflict is detected while
 * persisting an entity.
 */
public class OptimisticLockException extends RuntimeException {

    public OptimisticLockException(String message) {
        super(message);
    }

    public OptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
