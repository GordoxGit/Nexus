package com.example.hikabrain;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.example.hikabrain.ui.FeedbackServiceImpl;
import com.example.hikabrain.ui.ThemeServiceImpl;
import com.example.hikabrain.ui.UiServiceImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HBCommand implements CommandExecutor, TabCompleter {

    private final GameManager game;
    public HBCommand(GameManager game) { this.game = game; }

    private void sendHelp(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== HikaBrain ===");
        s.sendMessage(ChatColor.YELLOW + "/hb help" + ChatColor.GRAY + " - Affiche l'aide");
        s.sendMessage(ChatColor.YELLOW + "/hb list" + ChatColor.GRAY + " - Liste des arènes sauvegardées");
        s.sendMessage(ChatColor.YELLOW + "/hb setbed" + ChatColor.GRAY + " - Donne l'outil pour définir les lits");
        s.sendMessage(ChatColor.YELLOW + "/hb setbroke" + ChatColor.GRAY + " - Donne l'outil pour définir la zone cassable");
        s.sendMessage(ChatColor.YELLOW + "/hb snapshotbroke" + ChatColor.GRAY + " - Regénère le snapshot du pont");
        s.sendMessage(ChatColor.YELLOW + "/hb resetbroke" + ChatColor.GRAY + " - Réinitialise manuellement le pont");
        s.sendMessage(ChatColor.YELLOW + "/hb join [red|blue]" + ChatColor.GRAY + " - Rejoindre une équipe");
        s.sendMessage(ChatColor.YELLOW + "/hb leave" + ChatColor.GRAY + " - Quitter la partie");
        s.sendMessage(ChatColor.YELLOW + "/hb ui reload" + ChatColor.GRAY + " - Recharge la configuration UI");
        s.sendMessage(ChatColor.YELLOW + "/hb theme <id>" + ChatColor.GRAY + " - Applique un thème");
        s.sendMessage(ChatColor.DARK_GRAY + "Admin: create, setspawn, setbuildpos, setpoints, settimer, save, load, start, stop");
    }

    private boolean needAdmin(CommandSender s) {
        if (!s.hasPermission("hikabrain.admin")) {
            s.sendMessage(ChatColor.RED + "Permission requise: hikabrain.admin");
            return true;
        }
        return false;
    }

    private boolean ensureAllowedWorld(Player p, CommandSender s) {
        if (!HikaBrainPlugin.get().isWorldAllowed(p.getWorld())) {
            s.sendMessage(ChatColor.RED + "Actif uniquement dans: " + HikaBrainPlugin.get().allowedWorldsPretty());
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(sender); return true; }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list": {
                List<String> list = game.listArenas();
                if (list.isEmpty()) sender.sendMessage(ChatColor.GRAY + "Aucune arène sauvegardée.");
                else sender.sendMessage(ChatColor.YELLOW + "Arènes: " + String.join(", ", list));
                return true;
            }
            case "setbed": {
                if (needAdmin(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage("In-game only"); return true; }
                Player p = (Player) sender;
                p.getInventory().addItem(GameListener.bedSelectorItem());
                sender.sendMessage(ChatColor.GREEN + "Houe 'SetBed' ajoutée. Clic droit = lit rouge, clic gauche = lit bleu.");
                return true;
            }
            
            case "setbroke": {
                if (needAdmin(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage("In-game only"); return true; }
                Player p = (Player) sender;
                p.getInventory().addItem(GameListener.brokeSelectorItem());
                sender.sendMessage(ChatColor.GREEN + "Pelle 'setbroke' ajoutée. Clic gauche = pos1, clic droit = pos2.");
                return true;
            }
            case "snapshotbroke": {
                if (needAdmin(sender)) return true;
                game.snapshotBroke();
                sender.sendMessage(ChatColor.GREEN + "Snapshot broke lancé.");
                return true;
            }
            case "resetbroke": {
                if (needAdmin(sender)) return true;
                game.resetBroke();
                sender.sendMessage(ChatColor.GREEN + "Reset broke lancé.");
                return true;
            }
            case "ui": {
                if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                    if (needAdmin(sender)) return true;
                    HikaBrainPlugin pl = HikaBrainPlugin.get();
                    pl.reloadConfig();
                    if (pl.theme() instanceof ThemeServiceImpl ts) ts.reload();
                    if (pl.fx() instanceof FeedbackServiceImpl fs) fs.reload();
                    if (pl.ui() instanceof UiServiceImpl us) us.reload();
                    sender.sendMessage(ChatColor.GREEN + "UI rechargée.");
                    return true;
                }
                sender.sendMessage(ChatColor.RED + "Usage: /hb ui reload");
                return true;
            }
            case "theme": {
                if (needAdmin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Thèmes: " + String.join(", ", HikaBrainPlugin.get().theme().available()));
                    return true;
                }
                if (game.arena() == null) { sender.sendMessage(ChatColor.RED + "Aucune arène."); return true; }
                HikaBrainPlugin.get().theme().applyTheme(game.arena(), args[1]);
                sender.sendMessage(ChatColor.GREEN + "Thème appliqué: " + args[1]);
                return true;
            }
case "join": {
                if (!(sender instanceof Player)) { sender.sendMessage("In-game only"); return true; }
                Player p = (Player) sender;
                if (!ensureAllowedWorld(p, sender)) return true;
                Team pref = null;
                if (args.length >= 2) {
                    if (args[1].equalsIgnoreCase("red")) pref = Team.RED;
                    else if (args[1].equalsIgnoreCase("blue")) pref = Team.BLUE;
                }
                game.join(p, pref);
                return true;
            }
            case "leave": {
                if (!(sender instanceof Player)) { sender.sendMessage("In-game only"); return true; }
                game.leave((Player) sender);
                return true;
            }
            case "create": {
                if (needAdmin(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage("In-game only"); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /hb create <nom>"); return true; }
                Player p = (Player) sender;
                if (!ensureAllowedWorld(p, sender)) return true;
                World w = p.getWorld();
                game.createArena(args[1], w);
                sender.sendMessage(ChatColor.GREEN + "Arène '" + args[1] + "' créée pour " + w.getName());
                return true;
            }
            case "setspawn": {
                if (needAdmin(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage("In-game only"); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /hb setspawn <red|blue>"); return true; }
                Player p = (Player) sender;
                if (!ensureAllowedWorld(p, sender)) return true;
                Team t = args[1].equalsIgnoreCase("red") ? Team.RED :
                         args[1].equalsIgnoreCase("blue") ? Team.BLUE : null;
                if (t == null) { sender.sendMessage(ChatColor.RED + "Equipe invalide"); return true; }
                if (game.setSpawn(t, p.getLocation())) sender.sendMessage(ChatColor.GREEN + "Spawn " + t.name() + " défini.");
                else sender.sendMessage(ChatColor.RED + "Crée d'abord une arène.");
                return true;
            }
            case "setbuildpos": {
                if (needAdmin(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage("In-game only"); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /hb setbuildpos <1|2>"); return true; }
                Player p = (Player) sender;
                if (!ensureAllowedWorld(p, sender)) return true;
                int idx = args[1].equals("2") ? 2 : 1;
                if (game.setBuildCorner(idx, p.getLocation())) sender.sendMessage(ChatColor.GREEN + "Build pos" + idx + " OK");
                else sender.sendMessage(ChatColor.RED + "Crée d'abord une arène.");
                return true;
            }
            case "setpoints": {
                if (needAdmin(sender)) return true;
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /hb setpoints <n>"); return true; }
                try { int n = Integer.parseInt(args[1]); game.setTargetPoints(n); sender.sendMessage(ChatColor.GREEN + "Points cibles = " + n); }
                catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "Nombre invalide."); }
                return true;
            }
            case "settimer": {
                if (needAdmin(sender)) return true;
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /hb settimer <minutes>"); return true; }
                try { int m = Integer.parseInt(args[1]); game.setTimeLimitMinutes(m); sender.sendMessage(ChatColor.GREEN + "Timer = " + m + " min"); }
                catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "Nombre invalide."); }
                return true;
            }
            case "save": {
                if (needAdmin(sender)) return true;
                try { if (game.saveArena()) sender.sendMessage(ChatColor.GREEN + "Arène sauvegardée."); else sender.sendMessage(ChatColor.RED + "Rien à sauvegarder."); }
                catch (java.io.IOException e) { sender.sendMessage(ChatColor.RED + "Erreur: " + e.getMessage()); }
                return true;
            }
            case "load": {
                if (needAdmin(sender)) return true;
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /hb load <nom>"); return true; }
                try { if (game.loadArena(args[1])) sender.sendMessage(ChatColor.GREEN + "Arène '" + args[1] + "' chargée."); }
                catch (java.io.IOException e) { sender.sendMessage(ChatColor.RED + "Erreur: " + e.getMessage()); }
                return true;
            }
            case "start": { if (needAdmin(sender)) return true; game.start(); return true; }
            case "stop":  { if (needAdmin(sender)) return true; game.stop(true); return true; }
            default: { sendHelp(sender); return true; }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("help","list","setbed","join","leave","create","setspawn","setbuildpos","setpoints","settimer","save","load","start","stop","setbroke","snapshotbroke","resetbroke","ui","theme");
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "setspawn": return Arrays.asList("red","blue");
                case "setbuildpos": return Arrays.asList("1","2");
                case "setpoints": return Arrays.asList("5","10");
                case "settimer": return Arrays.asList("10","15","20");
                case "join": return Arrays.asList("red","blue");
                case "ui": return Arrays.asList("reload");
                case "theme": return new ArrayList<>(HikaBrainPlugin.get().theme().available());
            }
        }
        return new ArrayList<>();
    }
}
