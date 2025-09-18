package com.heneria.nexus.scheduler;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Task executed by the ring scheduler.
 */
final class RingTask {

    private final String id;
    private final Runnable runnable;
    private final EnumSet<GamePhase> phases;
    private volatile long intervalTicks;
    private final int offset;

    RingTask(String id, Runnable runnable, EnumSet<GamePhase> phases, long intervalTicks, int offset) {
        this.id = Objects.requireNonNull(id, "id");
        this.runnable = Objects.requireNonNull(runnable, "runnable");
        this.phases = phases.isEmpty() ? EnumSet.allOf(GamePhase.class) : phases.clone();
        this.intervalTicks = Math.max(1, intervalTicks);
        this.offset = offset;
    }

    String id() {
        return id;
    }

    Runnable runnable() {
        return runnable;
    }

    Set<GamePhase> phases() {
        return phases;
    }

    long intervalTicks() {
        return intervalTicks;
    }

    void updateInterval(long intervalTicks) {
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    boolean shouldExecute(long tick, GamePhase phase) {
        if (!phases.contains(phase)) {
            return false;
        }
        return (tick + offset) % intervalTicks == 0;
    }
}
