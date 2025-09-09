package fr.heneria.nexus.game.queue;

/**
 * Représente les différents modes de jeu disponibles pour le matchmaking.
 */
public enum GameMode {
    SOLO_1V1(2),
    TEAM_2V2(4);

    private final int requiredPlayers;

    GameMode(int requiredPlayers) {
        this.requiredPlayers = requiredPlayers;
    }

    /**
     * @return le nombre total de joueurs requis pour lancer une partie dans ce mode.
     */
    public int getRequiredPlayers() {
        return requiredPlayers;
    }
}
