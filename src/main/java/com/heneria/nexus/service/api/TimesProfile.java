package com.heneria.nexus.service.api;

import net.kyori.adventure.title.Title;

/**
 * Represents a named timing profile used when building Adventure titles.
 */
public interface TimesProfile {

    /**
     * Identifier of the profile as configured in {@code config.yml}.
     */
    String id();

    /**
     * Returns the Adventure times instance associated with the profile.
     */
    Title.Times times();
}
