package com.heneria.nexus.util;

import com.heneria.nexus.budget.BudgetService;
import com.heneria.nexus.budget.BudgetSnapshot;
import com.heneria.nexus.config.ConfigBundle;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.service.ServiceRegistry;
import com.heneria.nexus.watchdog.WatchdogReport;
import com.heneria.nexus.watchdog.WatchdogService;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;

/**
 * Collects runtime diagnostics for the dump command.
 */
public final class DumpUtil {

    private DumpUtil() {
    }

    public static List<Component> createDump(Server server,
                                             ConfigBundle bundle,
                                             ExecutorManager executorManager,
                                             RingScheduler scheduler,
                                             DbProvider dbProvider,
                                             ServiceRegistry serviceRegistry,
                                             BudgetService budgetService,
                                             WatchdogService watchdogService) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("=== État Nexus ===", NamedTextColor.GOLD));
        lines.add(Component.text("Serveur : " + server.getVersion(), NamedTextColor.YELLOW));
        lines.add(Component.text("Java : " + System.getProperty("java.version"), NamedTextColor.YELLOW));
        lines.add(Component.text("Fuseau horaire : " + bundle.core().timezone(), NamedTextColor.YELLOW));
        lines.add(Component.text("Mode : " + bundle.core().serverMode(), NamedTextColor.YELLOW));

        ExecutorManager.PoolStats poolDiagnostics = executorManager.stats();
        lines.add(Component.empty());
        lines.add(Component.text("-- Threads --", NamedTextColor.AQUA));
        appendPool(lines, poolDiagnostics.io());
        appendPool(lines, poolDiagnostics.compute());
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        lines.add(Component.text("Threads actifs : " + threadBean.getThreadCount(), NamedTextColor.GRAY));
        lines.add(Component.text("Pic threads : " + threadBean.getPeakThreadCount(), NamedTextColor.GRAY));

        RingScheduler.Diagnostics schedulerDiagnostics = scheduler.diagnostics();
        lines.add(Component.empty());
        lines.add(Component.text("-- Scheduler --", NamedTextColor.AQUA));
        lines.add(Component.text("Phase : " + schedulerDiagnostics.phase(), NamedTextColor.GRAY));
        lines.add(Component.text("Ticks : " + schedulerDiagnostics.ticks(), NamedTextColor.GRAY));
        lines.add(Component.text("Tâches exécutées : " + schedulerDiagnostics.executedTasks(), NamedTextColor.GRAY));
        lines.add(Component.text("Temps moyen (µs) : " + String.format(Locale.ROOT, "%.2f", schedulerDiagnostics.averageExecutionMicros()), NamedTextColor.GRAY));
        for (Map.Entry<String, Long> entry : schedulerDiagnostics.taskIntervals().entrySet()) {
            lines.add(Component.text(" • " + entry.getKey() + " -> " + entry.getValue() + " ticks", NamedTextColor.DARK_GRAY));
        }

        DbProvider.Diagnostics dbDiagnostics = dbProvider.diagnostics();
        lines.add(Component.empty());
        lines.add(Component.text("-- Base de données --", NamedTextColor.AQUA));
        lines.add(Component.text("Activée : " + (dbDiagnostics.settings() != null && dbDiagnostics.settings().enabled()), NamedTextColor.GRAY));
        lines.add(Component.text("Mode dégradé : " + dbDiagnostics.degraded(), NamedTextColor.GRAY));
        lines.add(Component.text("Connexions actives : " + dbDiagnostics.activeConnections(), NamedTextColor.GRAY));
        lines.add(Component.text("Connexions libres : " + dbDiagnostics.idleConnections(), NamedTextColor.GRAY));
        lines.add(Component.text("Connexions totales : " + dbDiagnostics.totalConnections(), NamedTextColor.GRAY));
        lines.add(Component.text("Threads en attente : " + dbDiagnostics.awaitingThreads(), NamedTextColor.GRAY));
        lines.add(Component.text("Tentatives échouées : " + dbDiagnostics.failedAttempts(), NamedTextColor.GRAY));

        lines.add(Component.empty());
        lines.add(Component.text("-- Services --", NamedTextColor.AQUA));
        serviceRegistry.snapshot().forEach(snapshot -> {
            String deps = snapshot.dependencies().isEmpty()
                    ? "—"
                    : snapshot.dependencies().stream().map(Class::getSimpleName).reduce((l, r) -> l + ", " + r).orElse("—");
            String state = "%s (healthy=%s, init=%dms, start=%dms, stop=%dms) deps=[%s]".formatted(
                    snapshot.serviceType().getSimpleName(),
                    snapshot.healthy(),
                    snapshot.initializationDuration().toMillis(),
                    snapshot.startDuration().toMillis(),
                    snapshot.stopDuration().toMillis(),
                    deps);
            if (snapshot.lastError().isPresent()) {
                state += " error=" + snapshot.lastError().get().getMessage();
            }
            lines.add(Component.text(state, NamedTextColor.GRAY));
        });

        WatchdogService.WatchdogStatistics watchdogStats = watchdogService.statistics();
        lines.add(Component.empty());
        lines.add(Component.text("-- Watchdog --", NamedTextColor.AQUA));
        lines.add(Component.text("Tâches surveillées : " + watchdogStats.monitoredTasks(), NamedTextColor.GRAY));
        lines.add(Component.text("Timeouts : " + watchdogStats.timedOutTasks(), NamedTextColor.GRAY));
        lines.add(Component.text("Temps moyen : " + String.format(Locale.ROOT, "%.2f ms", watchdogStats.averageDurationMillis()), NamedTextColor.GRAY));
        List<WatchdogReport> incidents = watchdogService.recentReports().stream()
                .filter(report -> report.status() != WatchdogReport.Status.COMPLETED)
                .limit(5)
                .toList();
        if (incidents.isEmpty()) {
            lines.add(Component.text("Aucun incident récent.", NamedTextColor.DARK_GRAY));
        } else {
            lines.add(Component.text("Incidents récents :", NamedTextColor.GRAY));
            incidents.forEach(report -> lines.add(Component.text(formatWatchdogReport(report), NamedTextColor.DARK_GRAY)));
        }

        Collection<BudgetSnapshot> budgets = budgetService.snapshots();
        if (!budgets.isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.text("-- Budgets --", NamedTextColor.AQUA));
            budgets.stream()
                    .sorted((left, right) -> left.arenaId().compareTo(right.arenaId()))
                    .forEach(snapshot -> lines.add(Component.text(
                            snapshot.arenaId() + " -> Entités=" + snapshot.entities() + "/" + snapshot.maxEntities()
                                    + " (pending=" + snapshot.pendingEntities() + ")"
                                    + " Items=" + snapshot.items() + "/" + snapshot.maxItems()
                                    + " (pending=" + snapshot.pendingItems() + ")"
                                    + " Projectiles=" + snapshot.projectiles() + "/" + snapshot.maxProjectiles()
                                    + " (pending=" + snapshot.pendingProjectiles() + ")"
                                    + " Particules=" + snapshot.particles() + "/" + snapshot.particlesSoftCap()
                                    + " (Hard=" + snapshot.particlesHardCap() + ")",
                            NamedTextColor.GRAY)));
        }

        lines.add(Component.empty());
        lines.add(Component.text("Chargé le : " + DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .withZone(bundle.core().timezone())
                .format(bundle.loadedAt()), NamedTextColor.DARK_GRAY));
        return lines;
    }

    private static void appendPool(List<Component> lines, ExecutorManager.PoolSnapshot snapshot) {
        String mode = snapshot.virtual() ? "virtuel" : "%d threads".formatted(snapshot.configuredThreads());
        lines.add(Component.text(snapshot.name().toUpperCase(Locale.ROOT) + " -> mode=" + mode
                + " actifs=" + snapshot.activeTasks()
                + " file=" + snapshot.queuedTasks()
                + " soumis=" + snapshot.submittedTasks()
                + " terminés=" + snapshot.completedTasks()
                + " rejetés=" + snapshot.rejectedTasks()
                + " avg=" + String.format(Locale.ROOT, "%.2fms", snapshot.averageExecutionMillis()), NamedTextColor.GRAY));
    }

    private static String formatWatchdogReport(WatchdogReport report) {
        StringBuilder builder = new StringBuilder(" • ")
                .append(report.taskName())
                .append(" [")
                .append(report.status())
                .append("] ")
                .append(report.duration().toMillis())
                .append(" ms");
        report.error().ifPresent(error -> {
            builder.append(" -> ")
                    .append(error.getClass().getSimpleName());
            if (error.getMessage() != null && !error.getMessage().isBlank()) {
                builder.append(": ").append(error.getMessage());
            }
        });
        return builder.toString();
    }
}
