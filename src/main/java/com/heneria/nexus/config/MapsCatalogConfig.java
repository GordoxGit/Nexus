package com.heneria.nexus.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable view over the maps catalogue configuration.
 */
public final class MapsCatalogConfig {

    private final RotationSettings rotation;
    private final List<MapEntry> maps;

    public MapsCatalogConfig(RotationSettings rotation, List<MapEntry> maps) {
        this.rotation = Objects.requireNonNull(rotation, "rotation");
        this.maps = List.copyOf(Objects.requireNonNull(maps, "maps"));
    }

    public RotationSettings rotation() {
        return rotation;
    }

    public List<MapEntry> maps() {
        return maps;
    }

    public record RotationSettings(boolean enabled,
                                   boolean weightedPick,
                                   boolean vetoVote,
                                   int minVotesToPick) {
        public RotationSettings {
            if (minVotesToPick < 0) {
                throw new IllegalArgumentException("minVotesToPick must be >= 0");
            }
        }
    }

    public record MapEntry(String id, String displayName, int weight, List<String> modes) {
        public MapEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(modes, "modes");
            if (!id.matches("[a-zA-Z0-9_]+")) {
                throw new IllegalArgumentException("id must be alphanumeric with optional underscore");
            }
            if (weight <= 0) {
                throw new IllegalArgumentException("weight must be > 0");
            }
        }

        public String normalizedId() {
            return id.toLowerCase(Locale.ROOT);
        }
    }
}
