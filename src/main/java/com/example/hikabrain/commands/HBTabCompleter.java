package com.example.hikabrain.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HBTabCompleter implements TabCompleter {

    private static final List<String> USER_CMDS = Arrays.asList(
            "help", "list", "join", "leave");

    private static final List<String> ADMIN_CMDS = Arrays.asList(
            "start", "stop", "create", "setspawn", "setbed", "setbroke",
            "setlobby", "setbuildpos", "setpoints", "settimer", "save",
            "load", "admin", "ui", "theme", "snapshotbroke", "resetbroke");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            for (String cmd : USER_CMDS) {
                if (cmd.startsWith(prefix)) list.add(cmd);
            }
            if (sender.hasPermission("hikabrain.admin")) {
                for (String cmd : ADMIN_CMDS) {
                    if (cmd.startsWith(prefix)) list.add(cmd);
                }
            }
            return list;
        }
        return new ArrayList<>();
    }
}
