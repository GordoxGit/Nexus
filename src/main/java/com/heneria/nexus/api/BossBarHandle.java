package com.heneria.nexus.api;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/**
 * Handle representing an attached boss bar instance.
 */
public interface BossBarHandle {

    /**
     * Returns the underlying Adventure boss bar instance.
     *
     * @return Adventure boss bar backing this handle
     */
    BossBar bossBar();

    /**
     * Returns the audience currently targeted by the bar.
     *
     * @return audience receiving the bar updates
     */
    Audience audience();

    /**
     * Returns the profile driving this bar.
     *
     * @return profile configuration used to render the bar
     */
    BossBarProfile profile();

    /**
     * Updates the textual component displayed by the bar.
     *
     * @param name new title component to display
     */
    void updateName(Component name);

    /**
     * Updates the progress between {@code 0.0} and {@code 1.0}.
     *
     * @param progress new progress value between zero and one
     */
    void updateProgress(float progress);

    /**
     * Shows the bar to its audience if hidden.
     */
    void show();

    /**
     * Hides the bar from its audience.
     */
    void hide();

    /**
     * Detaches and cleans up the bar. Further updates become no-ops.
     */
    void detach();

    /**
     * Returns whether the handle has been detached.
     *
     * @return {@code true} when the handle is detached
     */
    boolean detached();
}
