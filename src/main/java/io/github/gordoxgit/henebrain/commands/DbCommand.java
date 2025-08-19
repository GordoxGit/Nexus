package io.github.gordoxgit.henebrain.commands;

import com.zaxxer.hikari.HikariDataSource;
import io.github.gordoxgit.henebrain.database.HikariDataSourceProvider;
import io.github.gordoxgit.henebrain.database.FlywayManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;

/**
 * Handles administrative database commands.
 */
public class DbCommand implements CommandExecutor {

    private final HikariDataSourceProvider provider;
    private final FlywayManager flywayManager;

    public DbCommand(HikariDataSourceProvider provider, FlywayManager flywayManager) {
        this.provider = provider;
        this.flywayManager = flywayManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("db")) {
            sender.sendMessage(ChatColor.RED + "Usage: /hb db <doctor|migrate>");
            return true;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "doctor" -> doctor(sender);
            case "migrate" -> {
                if (!sender.hasPermission("henebrain.admin.db")) {
                    sender.sendMessage(ChatColor.RED + "Permission required.");
                    return true;
                }
                flywayManager.migrate();
                sender.sendMessage(ChatColor.GREEN + "Migration executed.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        }
        return true;
    }

    private void doctor(CommandSender sender) {
        long start = System.currentTimeMillis();
        try (Connection conn = provider.getDataSource().getConnection()) {
            conn.createStatement().execute("SELECT 1");
            long time = System.currentTimeMillis() - start;
            sender.sendMessage(ChatColor.GREEN + "Database connection OK (" + time + "ms)");

            if (provider.getDataSource() instanceof HikariDataSource hikari) {
                var mx = hikari.getHikariPoolMXBean();
                sender.sendMessage(ChatColor.GRAY + "Pool max=" + hikari.getMaximumPoolSize()
                        + " active=" + mx.getActiveConnections()
                        + " idle=" + mx.getIdleConnections());
            }

            var info = flywayManager.info();
            var current = info.current() != null ? info.current().getVersion() : "none";
            sender.sendMessage(ChatColor.GRAY + "Flyway version=" + current + " pending=" + info.pending().size());
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Database check failed: " + e.getMessage());
        }
    }
}
