package com.heneria.nexus.service.api;

import java.util.List;
import java.util.Objects;

/**
 * Result of a map validation process.
 */
public record ValidationReport(boolean valid, List<String> warnings, List<String> errors) {

    public static ValidationReport success(List<String> warnings) {
        return new ValidationReport(true, List.copyOf(warnings), List.of());
    }

    public static ValidationReport failure(List<String> warnings, List<String> errors) {
        return new ValidationReport(false, List.copyOf(warnings), List.copyOf(errors));
    }

    public ValidationReport {
        Objects.requireNonNull(warnings, "warnings");
        Objects.requireNonNull(errors, "errors");
    }
}
