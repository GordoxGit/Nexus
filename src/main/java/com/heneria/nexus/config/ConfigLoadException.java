package com.heneria.nexus.config;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when parsing configuration files fails.
 */
public final class ConfigLoadException extends Exception {

    private final List<String> errors;

    public ConfigLoadException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public ConfigLoadException(List<String> errors) {
        super("Configuration invalid: " + String.join(", ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }
}
