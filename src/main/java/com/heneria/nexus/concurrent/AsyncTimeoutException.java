package com.heneria.nexus.concurrent;

/**
 * Exception thrown when an asynchronous computation exceeds its timeout.
 */
public final class AsyncTimeoutException extends RuntimeException {

    public AsyncTimeoutException(String message) {
        super(message);
    }

    public AsyncTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
