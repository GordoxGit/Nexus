package com.heneria.nexus.util;

import com.heneria.nexus.config.ConfigBundle;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.scheduler.RingScheduler;
import com.heneria.nexus.service.ExecutorPools;
import com.heneria.nexus.service.ServiceRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
                                             ExecutorPools executorPools,
                                             RingScheduler scheduler,
                                             DbProvider dbProvider,
                                             ServiceRegistry serviceRegistry) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("=== État Nexus ===", NamedTextColor.GOLD));
        lines.add(Component.text("Serveur : " + server.getVersion(), NamedTextColor.YELLOW));
        lines.add(Component.text("Java : " + System.getProperty("java.version"), NamedTextColor.YELLOW));
        lines.add(Component.text("Fuseau horaire : " + bundle.config().timezone(), NamedTextColor.YELLOW));
        lines.add(Component.text("Mode : " + bundle.config().serverMode(), NamedTextColor.YELLOW));

        ExecutorPools.Diagnostics poolDiagnostics = executorPools.diagnostics();
        lines.add(Component.empty());
        lines.add(Component.text("-- Threads --", NamedTextColor.AQUA));
        appendPool(lines, "IO", poolDiagnostics.io());
        appendPool(lines, "Compute", poolDiagnostics.compute());
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

        lines.add(Component.empty());
        lines.add(Component.text("Chargé le : " + DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .withZone(bundle.config().timezone())
                .format(bundle.loadedAt()), NamedTextColor.DARK_GRAY));
        return lines;
    }

    private static void appendPool(List<Component> lines, String name, ExecutorPools.PoolSnapshot snapshot) {
        lines.add(Component.text(name + " -> core=" + snapshot.corePoolSize()
                + " actifs=" + snapshot.activeCount()
                + " file=" + snapshot.queuedTasks()
                + " complétés=" + snapshot.completedTasks(), NamedTextColor.GRAY));
    }
}
