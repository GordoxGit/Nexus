package com.heneria.nexus.api;

import net.kyori.adventure.title.Title;

/**
 * Represents a named timing profile used when building Adventure titles.
 */
public interface TimesProfile {

    /**
     * Identifier of the profile as configured in {@code config.yml}.
     *
     * @return identifier of the timing profile
     */
    String id();

    /**
     * Returns the Adventure times instance associated with the profile.
     *
     * @return Adventure title timing information
     */
    Title.Times times();
}
