package fr.heneria.nexus.ranking;

/**
 * Provides methods related to Elo rating calculations.
 */
public class EloCalculator {

    /**
     * Calculates the Elo rating change for a player.
     *
     * @param playerElo          current Elo of the player
     * @param opponentAverageElo average Elo of the opponents
     * @param playerWon          {@code true} if the player won the match
     * @param kFactor            adjustment factor
     * @return the Elo rating change rounded to the nearest integer
     */
    public int calculateEloChange(int playerElo, int opponentAverageElo, boolean playerWon, int kFactor) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentAverageElo - playerElo) / 400.0));
        double score = playerWon ? 1.0 : 0.0;
        double change = kFactor * (score - expected);
        return (int) Math.round(change);
    }
}
