package com.heneria.nexus.api;

import java.util.List;
import java.util.Objects;

/**
 * Result of a map validation process.
 *
 * @param valid whether the validation succeeded without blocking issues
 * @param warnings non-blocking issues detected during validation
 * @param errors blocking issues preventing the map from being used
 */
public record ValidationReport(boolean valid, List<String> warnings, List<String> errors) {

    /**
     * Creates a successful validation report.
     *
     * @param warnings non-blocking issues detected during validation
     * @return successful validation report
     */
    public static ValidationReport success(List<String> warnings) {
        return new ValidationReport(true, List.copyOf(warnings), List.of());
    }

    /**
     * Creates a failing validation report.
     *
     * @param warnings non-blocking issues detected during validation
     * @param errors blocking issues preventing the map from being used
     * @return failing validation report
     */
    public static ValidationReport failure(List<String> warnings, List<String> errors) {
        return new ValidationReport(false, List.copyOf(warnings), List.copyOf(errors));
    }

    /**
     * Validates constructor arguments.
     */
    public ValidationReport {
        Objects.requireNonNull(warnings, "warnings");
        Objects.requireNonNull(errors, "errors");
    }
}
