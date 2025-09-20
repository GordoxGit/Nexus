package com.heneria.nexus.service.api;

import java.util.List;

/**
 * Immutable snapshot of the queue state.
 */
public record QueueSnapshot(List<QueueTicket> tickets) {

    public QueueSnapshot {
        tickets = List.copyOf(tickets);
    }
}
