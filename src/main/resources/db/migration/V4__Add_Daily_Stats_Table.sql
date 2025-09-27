CREATE TABLE IF NOT EXISTS nexus_daily_stats (
    stat_date DATE NOT NULL PRIMARY KEY,
    total_matches_played INT NOT NULL DEFAULT 0,
    total_players_unique INT NOT NULL DEFAULT 0,
    total_coins_earned BIGINT NOT NULL DEFAULT 0,
    total_bpxp_earned BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
