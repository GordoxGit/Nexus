package com.heneria.nexus.api;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/**
 * Immutable description of an in-game capture point.
 *
 * @param id unique identifier of the capture point
 * @param displayName name presented to players when referencing the point
 */
public record CapturePoint(String id, Component displayName) {

    /**
     * Validates the canonical constructor arguments.
     */
    public CapturePoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
