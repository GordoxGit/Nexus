package com.heneria.nexus.api.region;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enumeration of the supported region flags.
 */
public enum RegionFlag {

    PVP_ENABLED("pvp.enabled", Set.of("pvp", "pvp_enabled", "pvp.enabled")),
    BUILD_ALLOWED("build.allowed", Set.of("build", "build_allowed", "build.allowed")),
    KNOCKBACK_REDUCTION("knockback.reduction", Set.of("knockback", "knockback_reduction", "knockback.reduction")),
    FALL_DAMAGE("fall_damage", Set.of("fall_damage", "fall.damage", "falldamage")),
    EFFECTS_APPLY("effects.apply", Set.of("effects", "effects_apply", "effects.apply"));

    private final String key;
    private final Set<String> aliases;

    RegionFlag(String key, Set<String> aliases) {
        this.key = Objects.requireNonNull(key, "key");
        this.aliases = normalizeAliases(aliases, key);
    }

    private Set<String> normalizeAliases(Set<String> aliases, String key) {
        Set<String> normalized = aliases.stream()
                .map(RegionFlag::normalize)
                .collect(Collectors.toSet());
        normalized.add(normalize(key));
        return normalized;
    }

    /**
     * Returns the canonical configuration key of the flag.
     *
     * @return canonical key using dot notation
     */
    public String key() {
        return key;
    }

    /**
     * Resolves a flag from an arbitrary configuration key.
     *
     * @param key raw configuration key
     * @return optional containing the resolved flag
     */
    public static Optional<RegionFlag> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(key);
        return Arrays.stream(values())
                .filter(flag -> flag.aliases.contains(normalized))
                .findFirst();
    }

    private static String normalize(String key) {
        String lower = key.toLowerCase(Locale.ROOT).replace(' ', '_');
        lower = lower.replace('-', '_');
        lower = lower.replace('.', '_');
        return lower;
    }
}
