package com.heneria.nexus.concurrent;

import com.heneria.nexus.util.NexusLogger;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * Default {@link SchedulerBridge} implementation relying on the Paper
 * scheduler. Tasks submitted from worker threads are queued and drained on the
 * main thread at a configurable tick interval.
 */
final class PaperSchedulerBridge implements SchedulerBridge {

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final Set<BukkitTask> repeatingTasks = ConcurrentHashMap.newKeySet();
    private final Object pumpLock = new Object();
    private volatile long checkIntervalTicks;
    private volatile BukkitTask pumpTask;
    private volatile boolean closed;

    PaperSchedulerBridge(JavaPlugin plugin, NexusLogger logger, long checkIntervalTicks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.checkIntervalTicks = Math.max(1L, checkIntervalTicks);
        startPump();
    }

    private void startPump() {
        synchronized (pumpLock) {
            if (pumpTask != null) {
                pumpTask.cancel();
            }
            BukkitScheduler scheduler = plugin.getServer().getScheduler();
            this.pumpTask = scheduler.runTaskTimer(plugin, this::drainQueue, 0L, checkIntervalTicks);
        }
    }

    void updateInterval(long newIntervalTicks) {
        long next = Math.max(1L, newIntervalTicks);
        if (this.checkIntervalTicks == next) {
            return;
        }
        this.checkIntervalTicks = next;
        if (!closed) {
            startPump();
        }
    }

    private void drainQueue() {
        if (closed || !plugin.isEnabled()) {
            queue.clear();
            return;
        }
        Runnable task;
        while ((task = queue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable throwable) {
                logger.error("Erreur lors de l'exécution d'une tâche main thread", throwable);
            }
        }
    }

    @Override
    public void runNow(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (closed || !plugin.isEnabled()) {
            return;
        }
        if (isOnMainThread()) {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                logger.error("Erreur lors de l'exécution d'une tâche main thread", throwable);
            }
            return;
        }
        queue.offer(runnable);
    }

    @Override
    public void runLater(Runnable runnable, long delayTicks) {
        Objects.requireNonNull(runnable, "runnable");
        if (closed || !plugin.isEnabled()) {
            return;
        }
        long delay = Math.max(0L, delayTicks);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> runNow(runnable), delay);
    }

    @Override
    public BukkitTask runRepeating(Runnable runnable, long delayTicks, long periodTicks) {
        Objects.requireNonNull(runnable, "runnable");
        if (closed || !plugin.isEnabled()) {
            return null;
        }
        long delay = Math.max(0L, delayTicks);
        long period = Math.max(1L, periodTicks);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> runNow(runnable), delay, period);
        repeatingTasks.add(task);
        return task;
    }

    @Override
    public boolean isOnMainThread() {
        return plugin.getServer().isPrimaryThread();
    }

    @Override
    public void close() {
        closed = true;
        queue.clear();
        synchronized (pumpLock) {
            if (pumpTask != null) {
                pumpTask.cancel();
                pumpTask = null;
            }
        }
        for (BukkitTask task : repeatingTasks) {
            try {
                task.cancel();
            } catch (Exception ignored) {
                // Ignore – tasks may already be cancelled by Paper.
            }
        }
        repeatingTasks.clear();
    }
}
