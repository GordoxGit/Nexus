package com.heneria.nexus.api;

/**
 * Additional parameters used when enqueuing a player.
 *
 * @param vip whether the player should be treated as VIP
 * @param weight matchmaking weight applied to the player
 */
public record QueueOptions(boolean vip, int weight) {

    /**
     * Validates constructor arguments.
     */
    public QueueOptions {
        if (weight < 0) {
            throw new IllegalArgumentException("weight must be >= 0");
        }
    }

    /**
     * Returns queue options with default values.
     *
     * @return default queue options
     */
    public static QueueOptions standard() {
        return new QueueOptions(false, 0);
    }
}
