package com.heneria.nexus.scheduler;

import com.heneria.nexus.config.NexusConfig;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.NexusLogger;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Phase aware scheduler that distributes work across ticks.
 *
 * <p>It uses the traditional Bukkit scheduler as the plugin targets the
 * non-Folia branch of Paper. The design keeps the door open for the
 * GlobalRegionScheduler documented in
 * https://docs.papermc.io/paper/dev/scheduler if we migrate later.</p>
 */
public final class RingScheduler implements LifecycleAware {

    public enum TaskProfile {
        HUD,
        SCOREBOARD
    }

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final AtomicReference<GamePhase> phase = new AtomicReference<>(GamePhase.LOBBY);
    private final AtomicLong tick = new AtomicLong();
    private final LongAdder executedTasks = new LongAdder();
    private final LongAdder totalRuntimeNanos = new LongAdder();
    private final ConcurrentMap<String, RingTask> tasks = new ConcurrentHashMap<>();
    private final Map<TaskProfile, Long> profileIntervals = new EnumMap<>(TaskProfile.class);
    private volatile BukkitTask tickerTask;

    public RingScheduler(JavaPlugin plugin, NexusLogger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        profileIntervals.put(TaskProfile.HUD, 4L);
        profileIntervals.put(TaskProfile.SCOREBOARD, 7L);
    }

    public void applyPerfSettings(NexusConfig.PerfSettings perfSettings) {
        profileIntervals.put(TaskProfile.HUD, hzToTicks(perfSettings.hudHz()));
        profileIntervals.put(TaskProfile.SCOREBOARD, hzToTicks(perfSettings.scoreboardHz()));
        tasks.values().forEach(task -> {
            if (task.id().startsWith("profile-")) {
                TaskProfile profile = TaskProfile.valueOf(task.id().substring("profile-".length()).toUpperCase());
                task.updateInterval(profileIntervals.getOrDefault(profile, task.intervalTicks()));
            }
        });
    }

    public void registerProfile(TaskProfile profile, EnumSet<GamePhase> phases, Runnable runnable) {
        String id = "profile-" + profile.name().toLowerCase();
        long interval = profileIntervals.getOrDefault(profile, 4L);
        tasks.put(id, new RingTask(id, runnable, phases, interval, profile.ordinal()));
    }

    public void registerTask(String id, long intervalTicks, EnumSet<GamePhase> phases, Runnable runnable) {
        int offset = Math.abs(id.hashCode());
        tasks.put(id, new RingTask(id, runnable, phases, intervalTicks, offset));
    }

    public void setPhase(GamePhase phase) {
        this.phase.set(Objects.requireNonNull(phase, "phase"));
    }

    @Override
    public CompletableFuture<Void> start() {
        if (tickerTask != null) {
            return CompletableFuture.completedFuture(null);
        }
        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        tasks.clear();
        return CompletableFuture.completedFuture(null);
    }

    private void tick() {
        long currentTick = tick.incrementAndGet();
        GamePhase currentPhase = phase.get();
        long tickStart = System.nanoTime();
        tasks.values().forEach(task -> executeTask(task, currentTick, currentPhase));
        long duration = System.nanoTime() - tickStart;
        if (duration > TimeUnit.MILLISECONDS.toNanos(2)) {
            long nanos = duration;
            logger.debug(() -> "Tick " + currentTick + " du RingScheduler a pris " + nanos + "ns");
        }
    }

    private void executeTask(RingTask task, long currentTick, GamePhase phase) {
        if (!task.shouldExecute(currentTick, phase)) {
            return;
        }
        long start = System.nanoTime();
        try {
            task.runnable().run();
        } catch (Throwable throwable) {
            logger.error("Erreur lors de l'exécution de la tâche " + task.id(), throwable);
        } finally {
            long duration = System.nanoTime() - start;
            executedTasks.increment();
            totalRuntimeNanos.add(duration);
        }
    }

    private long hzToTicks(int hz) {
        return Math.max(1L, Math.round(20.0d / Math.max(1, hz)));
    }

    public Diagnostics diagnostics() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        tasks.forEach((key, value) -> snapshot.put(key, value.intervalTicks()));
        return new Diagnostics(
                tick.get(),
                executedTasks.sum(),
                totalRuntimeNanos.sum(),
                phase.get(),
                Map.copyOf(snapshot));
    }

    public record Diagnostics(long ticks, long executedTasks, long totalRuntimeNanos, GamePhase phase, Map<String, Long> taskIntervals) {
        public double averageExecutionMicros() {
            if (executedTasks == 0L) {
                return 0D;
            }
            return (totalRuntimeNanos / 1_000D) / executedTasks;
        }
    }
}
