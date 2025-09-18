package com.heneria.nexus.util;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple logger wrapper adding the Nexus prefix and convenient helpers.
 */
public final class NexusLogger {

    private final Logger delegate;
    private final String prefix;

    public NexusLogger(Logger delegate, String prefix) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    private String format(String message) {
        return prefix + message;
    }

    private String format(Supplier<String> supplier) {
        return format(supplier.get());
    }

    public void info(String message) {
        delegate.log(Level.INFO, format(message));
    }

    public void info(Supplier<String> supplier) {
        delegate.log(Level.INFO, format(supplier));
    }

    public void warn(String message) {
        delegate.log(Level.WARNING, format(message));
    }

    public void warn(String message, Throwable throwable) {
        delegate.log(Level.WARNING, format(message), throwable);
    }

    public void error(String message) {
        delegate.log(Level.SEVERE, format(message));
    }

    public void error(String message, Throwable throwable) {
        delegate.log(Level.SEVERE, format(message), throwable);
    }

    public void debug(String message) {
        delegate.log(Level.FINE, format(message));
    }

    public void debug(Supplier<String> supplier) {
        delegate.log(Level.FINE, format(supplier));
    }

    public Logger unwrap() {
        return delegate;
    }
}
