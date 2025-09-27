package com.heneria.nexus.analytics.daily;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable representation of a row stored in the {@code nexus_daily_stats} table.
 */
public record DailyStatsSnapshot(LocalDate statDate,
                                 int totalMatchesPlayed,
                                 int totalPlayersUnique,
                                 long totalCoinsEarned,
                                 long totalBattlePassXpEarned) {

    public DailyStatsSnapshot {
        Objects.requireNonNull(statDate, "statDate");
        if (totalMatchesPlayed < 0) {
            throw new IllegalArgumentException("totalMatchesPlayed must be >= 0");
        }
        if (totalPlayersUnique < 0) {
            throw new IllegalArgumentException("totalPlayersUnique must be >= 0");
        }
        if (totalCoinsEarned < 0) {
            throw new IllegalArgumentException("totalCoinsEarned must be >= 0");
        }
        if (totalBattlePassXpEarned < 0) {
            throw new IllegalArgumentException("totalBattlePassXpEarned must be >= 0");
        }
    }
}
