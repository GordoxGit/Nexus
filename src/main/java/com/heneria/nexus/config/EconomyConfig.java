package com.heneria.nexus.config;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of the base economy configuration.
 */
public final class EconomyConfig {

    private final CoinsSettings coins;
    private final BattlePassSettings battlePass;
    private final ShopSettings shop;

    public EconomyConfig(CoinsSettings coins, BattlePassSettings battlePass, ShopSettings shop) {
        this.coins = Objects.requireNonNull(coins, "coins");
        this.battlePass = Objects.requireNonNull(battlePass, "battlePass");
        this.shop = Objects.requireNonNull(shop, "shop");
    }

    public CoinsSettings coins() {
        return coins;
    }

    public BattlePassSettings battlePass() {
        return battlePass;
    }

    public ShopSettings shop() {
        return shop;
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

    public record ShopSettings(Map<String, ClassEntry> classes, Map<String, CosmeticEntry> cosmetics) {
        public ShopSettings {
            Objects.requireNonNull(classes, "classes");
            Objects.requireNonNull(cosmetics, "cosmetics");
            classes = Map.copyOf(classes);
            cosmetics = Map.copyOf(cosmetics);
        }

        @Override
        public Map<String, ClassEntry> classes() {
            return classes;
        }

        @Override
        public Map<String, CosmeticEntry> cosmetics() {
            return cosmetics;
        }
    }

    public record ClassEntry(long cost) {
        public ClassEntry {
            if (cost < 0L) {
                throw new IllegalArgumentException("Class cost must be >= 0");
            }
        }
    }

    public record CosmeticEntry(String type, long cost) {
        public CosmeticEntry {
            Objects.requireNonNull(type, "type");
            if (type.isBlank()) {
                throw new IllegalArgumentException("cosmetic type must not be blank");
            }
            if (cost < 0L) {
                throw new IllegalArgumentException("Cosmetic cost must be >= 0");
            }
        }
    }
}
