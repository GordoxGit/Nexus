package com.heneria.nexus.api.region;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.util.BoundingBox;

/**
 * Immutable representation of a named cuboid region defined in a map.
 */
public record Region(String id,
                     BoundingBox bounds,
                     Map<RegionFlag, Object> flags) {

    public Region {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(flags, "flags");
    }

    /**
     * Returns an immutable copy of the flags associated with this region.
     *
     * @return immutable map of region flags
     */
    @Override
    public Map<RegionFlag, Object> flags() {
        return Map.copyOf(flags);
    }

    /**
     * Computes the volume of the region.
     *
     * @return cuboid volume in blocks
     */
    public double volume() {
        double width = Math.abs(bounds.getMaxX() - bounds.getMinX());
        double height = Math.abs(bounds.getMaxY() - bounds.getMinY());
        double depth = Math.abs(bounds.getMaxZ() - bounds.getMinZ());
        return width * height * depth;
    }

    /**
     * Resolves the flag associated with the supplied identifier.
     *
     * @param flag flag identifier
     * @return optional value when present
     */
    public Object flagValue(RegionFlag flag) {
        Objects.requireNonNull(flag, "flag");
        return flags.get(flag);
    }

    /**
     * Creates a new {@link Builder} instance.
     *
     * @param id unique region identifier
     * @return a builder seeded with the provided id
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Builder used to simplify region creation when parsing map definitions.
     */
    public static final class Builder {

        private final String id;
        private BoundingBox bounds;
        private final EnumMap<RegionFlag, Object> flags = new EnumMap<>(RegionFlag.class);

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder bounds(BoundingBox bounds) {
            this.bounds = Objects.requireNonNull(bounds, "bounds");
            return this;
        }

        public Builder flag(RegionFlag flag, Object value) {
            flags.put(Objects.requireNonNull(flag, "flag"), value);
            return this;
        }

        public Region build() {
            Objects.requireNonNull(bounds, "bounds");
            return new Region(id, bounds, Map.copyOf(flags));
        }
    }
}
