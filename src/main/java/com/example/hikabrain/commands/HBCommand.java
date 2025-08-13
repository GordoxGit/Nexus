package com.example.hikabrain.commands;

import com.example.hikabrain.*;
import com.example.hikabrain.ui.FeedbackServiceImpl;
import com.example.hikabrain.ui.ThemeServiceImpl;
import com.example.hikabrain.ui.UiServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class HBCommand implements CommandExecutor {

    private final HikaBrainPlugin plugin;
    private final GameManager game;

    public HBCommand(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.game = plugin.game();
    }

    private void sendHelp(CommandSender s) {
        Component header = Component.text("HikaBrain (Heneria) ", NamedTextColor.AQUA)
                .append(Component.text("— Aide des commandes", NamedTextColor.GRAY));
        s.sendMessage(LegacyComponentSerializer.legacySection().serialize(header));
        line(s, "/hb join [red|blue]", "Rejoindre une équipe dans l'arène actuelle.", null);
        line(s, "/hb leave", "Quitter la partie et retourner au lobby.", null);
        line(s, "/hb admin [on|off]", "Activer le mode admin pour construire librement.", "hikabrain.admin");
        line(s, "/hb create <nom> <taille_equipe>", "Créer une nouvelle arène.", "hikabrain.admin");
        line(s, "/hb setspawn <red|blue>", "Définir le point d'apparition d'une équipe.", "hikabrain.admin");
        line(s, "/hb setlobby", "Définir le point de spawn du lobby.", "hikabrain.admin");
        line(s, "/hb start / stop", "Démarrer ou arrêter la partie.", "hikabrain.admin");
        line(s, "/hb ui reload", "Recharger la configuration de l'interface.", "hikabrain.admin");
        s.sendMessage(ChatColor.GRAY + "Utilisez l'horloge dans votre inventaire pour choisir une arène.");
    }

    private void line(CommandSender s, String cmd, String desc, String perm) {
        Component c = Component.text(cmd, NamedTextColor.YELLOW)
                .append(Component.text(" : " + desc, NamedTextColor.GRAY));
        if (perm != null) {
            c = c.append(Component.text(" (" + perm + ")", NamedTextColor.DARK_GRAY));
        }
        s.sendMessage(LegacyComponentSerializer.legacySection().serialize(c));
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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

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
            case "admin": {
                if (needAdmin(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage("In-game only"); return true; }
                Player p = (Player) sender;
                String arg = args.length >= 2 ? args[1].toLowerCase() : "toggle";
                AdminModeService am = HikaBrainPlugin.get().admin();
                boolean enabled;
                switch (arg) {
                    case "on": am.enable(p); enabled = true; break;
                    case "off": am.disable(p); enabled = false; break;
                    default: enabled = am.toggle(p); break;
                }
                sender.sendMessage(ChatColor.YELLOW + "Admin: " + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                return true;
            }
            case "ui": {
                if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                    if (needAdmin(sender)) return true;
                    HikaBrainPlugin pl = HikaBrainPlugin.get();
                    pl.reloadConfig();
                    pl.reloadServerInfo();
                    pl.reloadUiStyle();
                    if (pl.theme() instanceof ThemeServiceImpl ts) ts.reload();
                    if (pl.fx() instanceof FeedbackServiceImpl fs) fs.reload();
                    if (pl.ui() instanceof UiServiceImpl us) us.reload();
                    pl.scoreboard().reload();
                    pl.tablist().reload();
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
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /hb create <nom> <teamSize>"); return true; }
                int ts;
                try { ts = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "teamSize invalide"); return true; }
                if (ts < 1 || ts > 4) { sender.sendMessage(ChatColor.RED + "teamSize doit être 1-4"); return true; }
                Player p = (Player) sender;
                if (!ensureAllowedWorld(p, sender)) return true;
                World w = p.getWorld();
                game.createArena(args[1], w, ts);
                sender.sendMessage(ChatColor.GREEN + "Arène '" + args[1] + "' créée (mode " + ts + "v" + ts + ") pour " + w.getName());
                return true;
            }
            case "setlobby": {
                if (needAdmin(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage("In-game only"); return true; }
                Player p = (Player) sender;
                HikaBrainPlugin.get().setLobby(p.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Lobby défini.");
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
            case "start": {
                if (needAdmin(sender)) return true;
                game.start();
                return true;
            }
            case "stop": {
                if (needAdmin(sender)) return true;
                game.stop(true);
                return true;
            }
            default: {
                sendHelp(sender);
                return true;
            }
        }
    }
}
