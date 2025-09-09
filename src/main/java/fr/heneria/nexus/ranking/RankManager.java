package fr.heneria.nexus.ranking;

import fr.heneria.nexus.player.rank.PlayerRank;

import java.util.EnumMap;
import java.util.Map;

/**
 * Handles the association between Elo values and {@link PlayerRank}.
 */
public class RankManager {

    private static final RankManager INSTANCE = new RankManager();

    private final Map<PlayerRank, Integer> rankThresholds = new EnumMap<>(PlayerRank.class);

    private RankManager() {
        rankThresholds.put(PlayerRank.UNRANKED, 0);
        rankThresholds.put(PlayerRank.BRONZE, 800);
        rankThresholds.put(PlayerRank.SILVER, 1100);
        rankThresholds.put(PlayerRank.GOLD, 1400);
        rankThresholds.put(PlayerRank.PLATINUM, 1700);
        rankThresholds.put(PlayerRank.DIAMOND, 2000);
        rankThresholds.put(PlayerRank.MASTER, 2300);
        rankThresholds.put(PlayerRank.GRANDMASTER, 2600);
        rankThresholds.put(PlayerRank.CHAMPION, 2900);
    }

    public static RankManager getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the rank corresponding to the provided Elo value.
     *
     * @param elo current Elo rating
     * @return the matching {@link PlayerRank}
     */
    public PlayerRank getRankFromElo(int elo) {
        PlayerRank current = PlayerRank.UNRANKED;
        for (PlayerRank rank : PlayerRank.values()) {
            int threshold = rankThresholds.getOrDefault(rank, Integer.MAX_VALUE);
            if (elo >= threshold) {
                current = rank;
            } else {
                break;
            }
        }
        return current;
    }
}
