package com.heneria.nexus.config;

import java.util.Objects;

/**
 * Immutable snapshot of the base economy configuration.
 */
public final class EconomyConfig {

    private final CoinsSettings coins;
    private final BattlePassSettings battlePass;

    public EconomyConfig(CoinsSettings coins, BattlePassSettings battlePass) {
        this.coins = Objects.requireNonNull(coins, "coins");
        this.battlePass = Objects.requireNonNull(battlePass, "battlePass");
    }

    public CoinsSettings coins() {
        return coins;
    }

    public BattlePassSettings battlePass() {
        return battlePass;
    }

    public record CoinsSettings(int winPerMatch, int losePerMatch, int firstWinBonus) {
        public CoinsSettings {
            if (winPerMatch < 0 || losePerMatch < 0 || firstWinBonus < 0) {
                throw new IllegalArgumentException("Economy rewards must be >= 0");
            }
            if (winPerMatch < losePerMatch) {
                throw new IllegalArgumentException("winPerMatch must be >= losePerMatch");
            }
        }
    }

    public record BattlePassSettings(int seasonDays, int tiers, XpPerMatch xpPerMatch) {
        public BattlePassSettings {
            if (seasonDays <= 0) {
                throw new IllegalArgumentException("seasonDays must be > 0");
            }
            if (tiers <= 0) {
                throw new IllegalArgumentException("tiers must be > 0");
            }
            Objects.requireNonNull(xpPerMatch, "xpPerMatch");
        }
    }

    public record XpPerMatch(int win, int lose) {
        public XpPerMatch {
            if (win < 0 || lose < 0) {
                throw new IllegalArgumentException("XP rewards must be >= 0");
            }
            if (win < lose) {
                throw new IllegalArgumentException("win XP must be >= lose XP");
            }
        }
    }
}
