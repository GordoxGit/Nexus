package com.heneria.nexusproxy.velocity.health;

/**
 * Enumerates the different availability states reported by Nexus servers.
 */
public enum ServerAvailability {
    LOBBY,
    STARTING,
    IN_GAME,
    ENDING,
    UNKNOWN,
    OFFLINE;

    /**
     * Returns {@code true} when the server can accept additional players.
     */
    public boolean isJoinable(int playerCount, int maxPlayers) {
        return switch (this) {
            case LOBBY, STARTING -> playerCount < maxPlayers;
            case UNKNOWN -> true;
            default -> false;
        };
    }
}
