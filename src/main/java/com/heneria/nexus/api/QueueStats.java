package com.heneria.nexus.api;

/**
 * Aggregated metrics about the matchmaking queues.
 *
 * @param totalPlayers number of players currently queued across all modes
 * @param vipPlayers number of VIP players currently queued
 * @param averageWaitSeconds average waiting time of players in seconds
 * @param matchesFormed total number of matches formed since boot
 */
public record QueueStats(int totalPlayers, int vipPlayers, long averageWaitSeconds, long matchesFormed) {
}
