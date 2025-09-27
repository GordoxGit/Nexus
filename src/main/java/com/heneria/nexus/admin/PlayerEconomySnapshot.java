package com.heneria.nexus.admin;

/**
 * Immutable representation of the persisted balance for a player.
 */
public record PlayerEconomySnapshot(long balance) {

    /**
     * Creates an empty snapshot with zero balance.
     *
     * @return empty economy snapshot
     */
    public static PlayerEconomySnapshot empty() {
        return new PlayerEconomySnapshot(0L);
    }
}
