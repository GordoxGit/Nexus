package com.heneria.nexus.api;

import java.util.List;

/**
 * Immutable snapshot of the queue state.
 *
 * @param tickets ordered view of queued tickets
 */
public record QueueSnapshot(List<QueueTicket> tickets) {

    /**
     * Copies the provided list to guarantee immutability.
     */
    public QueueSnapshot {
        tickets = List.copyOf(tickets);
    }
}
