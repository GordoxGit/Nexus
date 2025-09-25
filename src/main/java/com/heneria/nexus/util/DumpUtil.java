package com.heneria.nexus.util;

import com.heneria.nexus.budget.BudgetService;
import com.heneria.nexus.budget.BudgetSnapshot;
import com.heneria.nexus.config.ConfigBundle;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.hologram.HoloService;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.service.ServiceLifecycle;
import com.heneria.nexus.service.ServiceRegistry;
import com.heneria.nexus.service.ServiceStateSnapshot;
import com.heneria.nexus.watchdog.WatchdogReport;
import com.heneria.nexus.watchdog.WatchdogService;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Collects runtime diagnostics for the dump command.
 */
public final class DumpUtil {

    private DumpUtil() {
    }

    public static List<Component> createDump(JavaPlugin plugin,
                                             Server server,
                                             ConfigBundle bundle,
                                             ExecutorManager executorManager,
                                             RingScheduler scheduler,
                                             DbProvider dbProvider,
                                             ServiceRegistry serviceRegistry,
                                             BudgetService budgetService,
                                             WatchdogService watchdogService,
                                             HoloService holoService) {
        List<Component> lines = new ArrayList<>();
        PluginDescriptionFile description = plugin.getDescription();
        lines.add(Component.text("=== État Nexus ===", NamedTextColor.GOLD));
        lines.add(Component.text("Nexus : v" + nullSafe(description.getVersion()), NamedTextColor.YELLOW));
        lines.add(Component.text("Paper : " + server.getVersion(), NamedTextColor.YELLOW));
        lines.add(Component.text("Java : " + System.getProperty("java.version"), NamedTextColor.YELLOW));
        lines.add(Component.text("Fuseau horaire : " + formatZone(bundle.core().timezone()), NamedTextColor.YELLOW));
        lines.add(Component.text("Mode : " + bundle.core().serverMode(), NamedTextColor.YELLOW));
        lines.add(Component.text("Configuration : v" + bundle.version(), NamedTextColor.YELLOW));

        ExecutorManager.PoolStats poolDiagnostics = executorManager.stats();
        lines.add(Component.empty());
        lines.add(Component.text("-- Threads --", NamedTextColor.AQUA));
        appendPool(lines, poolDiagnostics.io());
        appendPool(lines, poolDiagnostics.compute());
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        lines.add(Component.text("Threads actifs : " + formatNumber(threadBean.getThreadCount()), NamedTextColor.GRAY));
        lines.add(Component.text("Pic threads : " + formatNumber(threadBean.getPeakThreadCount()), NamedTextColor.GRAY));

        RingScheduler.Diagnostics schedulerDiagnostics = scheduler.diagnostics();
        lines.add(Component.empty());
        lines.add(Component.text("-- Scheduler --", NamedTextColor.AQUA));
        lines.add(Component.text("Phase : " + schedulerDiagnostics.phase(), NamedTextColor.GRAY));
        lines.add(Component.text("Ticks : " + formatNumber(schedulerDiagnostics.ticks()), NamedTextColor.GRAY));
        lines.add(Component.text("Tâches exécutées : " + formatNumber(schedulerDiagnostics.executedTasks()), NamedTextColor.GRAY));
        lines.add(Component.text("Temps moyen : " + formatDouble(schedulerDiagnostics.averageExecutionMicros() / 1000D, "ms"), NamedTextColor.GRAY));
        schedulerDiagnostics.taskIntervals().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add(Component.text(" • " + entry.getKey() + " -> " + formatNumber(entry.getValue()) + " ticks", NamedTextColor.DARK_GRAY)));

        DbProvider.Diagnostics dbDiagnostics = dbProvider.diagnostics();
        lines.add(Component.empty());
        lines.add(Component.text("-- Base de données --", NamedTextColor.AQUA));
        boolean databaseEnabled = dbDiagnostics.settings() != null && dbDiagnostics.settings().enabled();
        lines.add(Component.text("Activée : " + (databaseEnabled ? "oui" : "non"),
                databaseEnabled ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY));
        lines.add(Component.text("Mode : " + (dbDiagnostics.degraded() ? "Dégradé" : "Normal"),
                stateColor(!dbDiagnostics.degraded())));
        lines.add(Component.text("Connexions actives : " + formatNumber(dbDiagnostics.activeConnections()), NamedTextColor.GRAY));
        lines.add(Component.text("Connexions libres : " + formatNumber(dbDiagnostics.idleConnections()), NamedTextColor.GRAY));
        lines.add(Component.text("Connexions totales : " + formatNumber(dbDiagnostics.totalConnections()), NamedTextColor.GRAY));
        lines.add(Component.text("Threads en attente : " + formatNumber(dbDiagnostics.awaitingThreads()), NamedTextColor.GRAY));
        lines.add(Component.text("Tentatives échouées : " + formatNumber(dbDiagnostics.failedAttempts()),
                dbDiagnostics.failedAttempts() > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));

        HoloService.Diagnostics holoDiagnostics = holoService.diagnostics();
        lines.add(Component.empty());
        lines.add(Component.text("-- Hologrammes --", NamedTextColor.AQUA));
        lines.add(Component.text("Actifs : " + formatNumber(holoDiagnostics.activeHolograms()), NamedTextColor.GRAY));
        lines.add(Component.text("Pool TextDisplay : " + formatNumber(holoDiagnostics.pooledTextDisplays()), NamedTextColor.GRAY));
        lines.add(Component.text("Pool Interaction : " + formatNumber(holoDiagnostics.pooledInteractions()), NamedTextColor.GRAY));

        lines.add(Component.empty());
        lines.add(Component.text("-- Services --", NamedTextColor.AQUA));
        serviceRegistry.snapshot().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.serviceType().getSimpleName()))
                .forEach(snapshot -> lines.add(formatServiceSnapshot(snapshot)));

        WatchdogService.WatchdogStatistics watchdogStats = watchdogService.statistics();
        lines.add(Component.empty());
        lines.add(Component.text("-- Watchdog --", NamedTextColor.AQUA));
        lines.add(Component.text("Tâches surveillées : " + formatNumber(watchdogStats.monitoredTasks()), NamedTextColor.GRAY));
        lines.add(Component.text("Timeouts : " + formatNumber(watchdogStats.timedOutTasks()),
                watchdogStats.timedOutTasks() > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
        lines.add(Component.text("Temps moyen : " + formatDouble(watchdogStats.averageDurationMillis(), "ms"), NamedTextColor.GRAY));
        List<WatchdogReport> incidents = watchdogService.recentReports().stream()
                .filter(report -> report.status() != WatchdogReport.Status.COMPLETED)
                .limit(5)
                .toList();
        if (incidents.isEmpty()) {
            lines.add(Component.text("Aucun incident récent.", NamedTextColor.DARK_GRAY));
        } else {
            lines.add(Component.text("Incidents récents :", NamedTextColor.GRAY));
            DateTimeFormatter incidentFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                    .withZone(bundle.core().timezone());
            incidents.forEach(report -> lines.add(formatWatchdogReport(report, incidentFormatter)));
        }

        Collection<BudgetSnapshot> budgets = budgetService.snapshots();
        if (!budgets.isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.text("-- Budgets --", NamedTextColor.AQUA));
            budgets.stream()
                    .sorted(Comparator.comparing(BudgetSnapshot::arenaId))
                    .forEach(snapshot -> lines.add(formatBudgetSnapshot(snapshot)));
        }

        lines.add(Component.empty());
        lines.add(Component.text("Chargé le : " + DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .withZone(bundle.core().timezone())
                .format(bundle.loadedAt()), NamedTextColor.DARK_GRAY));
        return lines;
    }

    private static void appendPool(List<Component> lines, ExecutorManager.PoolSnapshot snapshot) {
        String mode = snapshot.virtual() ? "virtuel" : "%d threads".formatted(snapshot.configuredThreads());
        StringBuilder builder = new StringBuilder(" • ")
                .append(snapshot.name().toUpperCase(Locale.ROOT))
                .append(" -> mode=")
                .append(mode)
                .append(" pool=")
                .append(formatNumber(snapshot.poolSize()))
                .append(" actifs=")
                .append(formatNumber(snapshot.activeTasks()))
                .append(" file=")
                .append(formatNumber(snapshot.queuedTasks()))
                .append(" soumis=")
                .append(formatNumber(snapshot.submittedTasks()))
                .append(" terminés=")
                .append(formatNumber(snapshot.completedTasks()))
                .append(" rejetés=")
                .append(formatNumber(snapshot.rejectedTasks()))
                .append(" avg=")
                .append(formatDouble(snapshot.averageExecutionMillis(), "ms"));
        NamedTextColor color = snapshot.rejectedTasks() > 0 ? NamedTextColor.RED : NamedTextColor.GRAY;
        lines.add(Component.text(builder.toString(), color));
    }

    private static Component formatServiceSnapshot(ServiceStateSnapshot snapshot) {
        String dependencies = snapshot.dependencies().isEmpty()
                ? "—"
                : snapshot.dependencies().stream()
                        .map(Class::getSimpleName)
                        .sorted()
                        .collect(Collectors.joining(", "));
        StringBuilder builder = new StringBuilder(" • ")
                .append(snapshot.serviceType().getSimpleName())
                .append(" -> état=")
                .append(snapshot.lifecycle())
                .append(" healthy=")
                .append(snapshot.healthy())
                .append(" init=")
                .append(formatDuration(snapshot.initializationDuration()))
                .append(" start=")
                .append(formatDuration(snapshot.startDuration()))
                .append(" stop=")
                .append(formatDuration(snapshot.stopDuration()))
                .append(" deps=[")
                .append(dependencies)
                .append(']');
        snapshot.lastError().ifPresent(error -> builder.append(" error=").append(error.getClass().getSimpleName())
                .append(optionalMessage(error.getMessage())));
        NamedTextColor color = stateColor(snapshot.lifecycle() != ServiceLifecycle.FAILED && snapshot.healthy());
        return Component.text(builder.toString(), color);
    }

    private static Component formatBudgetSnapshot(BudgetSnapshot snapshot) {
        StringBuilder builder = new StringBuilder(" • ")
                .append(snapshot.arenaId())
                .append(" [")
                .append(snapshot.mode())
                .append("/")
                .append(nullSafe(snapshot.mapId()))
                .append("] -> ")
                .append(formatBudget("Entités", snapshot.entities(), snapshot.maxEntities(), snapshot.pendingEntities()))
                .append(' ')
                .append(formatBudget("Items", snapshot.items(), snapshot.maxItems(), snapshot.pendingItems()))
                .append(' ')
                .append(formatBudget("Projectiles", snapshot.projectiles(), snapshot.maxProjectiles(), snapshot.pendingProjectiles()))
                .append(' ')
                .append("Particules=")
                .append(formatNumber(snapshot.particles()))
                .append('/')
                .append(formatNumber(snapshot.particlesSoftCap()))
                .append(" (hard=")
                .append(formatNumber(snapshot.particlesHardCap()))
                .append(')');
        return Component.text(builder.toString(), NamedTextColor.GRAY);
    }

    private static Component formatWatchdogReport(WatchdogReport report, DateTimeFormatter formatter) {
        StringBuilder builder = new StringBuilder(" • ")
                .append(formatter.format(report.timestamp()))
                .append(" — ")
                .append(report.taskName())
                .append(" [")
                .append(report.status())
                .append("] ")
                .append(formatDouble(report.duration().toNanos() / 1_000_000.0D, "ms"));
        report.error().ifPresent(error -> builder.append(" -> ")
                .append(error.getClass().getSimpleName())
                .append(optionalMessage(error.getMessage())));
        return Component.text(builder.toString(), NamedTextColor.RED);
    }

    private static NamedTextColor stateColor(boolean ok) {
        return ok ? NamedTextColor.GRAY : NamedTextColor.RED;
    }

    private static String formatBudget(String label, long used, long max, long pending) {
        return label + '=' + formatNumber(used) + '/' + formatNumber(max) + " (attente=" + formatNumber(pending) + ')';
    }

    private static String formatDuration(Duration duration) {
        double millis = duration == null ? 0D : duration.toNanos() / 1_000_000.0D;
        return formatDouble(millis, "ms");
    }

    private static String formatDouble(double value, String unit) {
        return String.format(Locale.ROOT, "%.2f %s", value, unit);
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatNumber(int value) {
        return formatNumber((long) value);
    }

    private static String nullSafe(String value) {
        return value == null ? "?" : value;
    }

    private static String optionalMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return ": " + message;
    }

    private static String formatZone(ZoneId zoneId) {
        return zoneId == null ? "?" : zoneId.getId();
    }
}
