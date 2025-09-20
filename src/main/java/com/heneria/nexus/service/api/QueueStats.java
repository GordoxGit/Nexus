package com.heneria.nexus.service.api;

/**
 * Aggregated metrics about the matchmaking queues.
 */
public record QueueStats(int totalPlayers, int vipPlayers, long averageWaitSeconds, long matchesFormed) {
}
