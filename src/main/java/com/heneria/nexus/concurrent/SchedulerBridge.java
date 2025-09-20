package com.heneria.nexus.concurrent;

import org.bukkit.scheduler.BukkitTask;

/**
 * Bridge allowing components to safely interact with the Paper main thread.
 */
public interface SchedulerBridge extends AutoCloseable {

    /**
     * Executes the runnable on the main thread as soon as possible. If the
     * current thread is already the main thread, the runnable is executed
     * directly.
     *
     * @param runnable task to execute
     */
    void runNow(Runnable runnable);

    /**
     * Schedules the runnable to run on the main thread after the provided
     * delay (in ticks).
     *
     * @param runnable task to execute
     * @param delayTicks delay in ticks before execution
     */
    void runLater(Runnable runnable, long delayTicks);

    /**
     * Schedules a repeating task on the main thread.
     *
     * @param runnable task to execute
     * @param delayTicks initial delay in ticks
     * @param periodTicks period in ticks between executions
     * @return Bukkit task handle that can be cancelled
     */
    BukkitTask runRepeating(Runnable runnable, long delayTicks, long periodTicks);

    /**
     * @return {@code true} if the current thread is the Paper main thread.
     */
    boolean isOnMainThread();

    /**
     * Cancels any scheduled tasks and clears the internal queue. No callback
     * will be executed after this method returns.
     */
    @Override
    void close();
}
