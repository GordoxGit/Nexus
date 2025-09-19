package com.heneria.nexus.service.api;

/**
 * Additional parameters used when enqueuing a player.
 */
public record QueueOptions(boolean vip, int weight) {

    public QueueOptions {
        if (weight < 0) {
            throw new IllegalArgumentException("weight must be >= 0");
        }
    }

    public static QueueOptions standard() {
        return new QueueOptions(false, 0);
    }
}
