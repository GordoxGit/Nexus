package fr.heneria.nexus.gui.admin;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import org.bukkit.Location;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Interface d'édition d'une arène spécifique.
 */
public class ArenaEditorGui {

    private final ArenaManager arenaManager;
    private final Arena arena;
    private final AdminPlacementManager adminPlacementManager;

    public ArenaEditorGui(ArenaManager arenaManager, Arena arena, AdminPlacementManager adminPlacementManager) {
        this.arenaManager = arenaManager;
        this.arena = arena;
        this.adminPlacementManager = adminPlacementManager;
    }

    /**
     * Ouvre le GUI d'édition pour le joueur donné.
     *
     * @param player joueur à qui ouvrir l'interface
     */
    public void open(Player player) {
        Component title = Component.text("Édition: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(arena.getName(), NamedTextColor.RED));

        Gui gui = Gui.gui()
                .title(title)
                .rows(3)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        // L'action de fermeture est définie une seule fois sur le GUI
        gui.setCloseGuiAction(event -> arenaManager.stopEditing(player.getUniqueId()));

        GuiItem info = ItemBuilder.from(Material.PAPER)
                .name(Component.text("Informations", NamedTextColor.YELLOW))
                .lore(
                        Component.text("Nom: " + arena.getName()),
                        Component.text("Joueurs max: " + arena.getMaxPlayers())
                )
                .asGuiItem();

        GuiItem setSpawns = ItemBuilder.from(Material.ARMOR_STAND)
                .name(Component.text("Définir les spawns", NamedTextColor.YELLOW))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    gui.setCloseGuiAction(closeEvent -> {});
                    new ArenaSpawnManagerGui(arenaManager, arena, adminPlacementManager).open(p);
                });

        GuiItem save = ItemBuilder.from(Material.ANVIL)
                .name(Component.text("Sauvegarder l'arène", NamedTextColor.DARK_GREEN))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    arenaManager.saveArena(arena);
                    ((Player) event.getWhoClicked()).sendMessage("§aArène '" + arena.getName() + "' sauvegardée avec succès !");
                });

        GuiItem teleport = ItemBuilder.from(Material.ENDER_PEARL)
                .name(Component.text("Se Téléporter à l'arène", NamedTextColor.AQUA))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    Location loc = null;
                    if (!arena.getSpawns().isEmpty()) {
                        int firstTeam = arena.getSpawns().keySet().stream().min(Integer::compareTo).orElse(1);
                        Map<Integer, Location> teamSpawns = arena.getSpawns().get(firstTeam);
                        if (teamSpawns != null && !teamSpawns.isEmpty()) {
                            int firstSpawn = teamSpawns.keySet().stream().min(Integer::compareTo).orElse(1);
                            loc = teamSpawns.get(firstSpawn);
                        }
                    }
                    if (loc != null) {
                        p.teleport(loc);
                    } else {
                        p.sendMessage("§cAucun spawn défini pour cette arène.");
                    }
                });

        GuiItem delete = ItemBuilder.from(Material.TNT)
                .name(Component.text("Supprimer l'arène", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    gui.setCloseGuiAction(closeEvent -> {});
                    new ConfirmDeleteGui(arenaManager, arena, adminPlacementManager).open(p);
                });

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ArenaListGui(arenaManager, AdminConversationManager.getInstance(), adminPlacementManager).open((Player) event.getWhoClicked());
                });

        gui.setItem(13, info);
        gui.setItem(11, setSpawns);
        gui.setItem(15, save);
        gui.setItem(20, teleport);
        gui.setItem(24, delete);
        gui.setItem(26, back);

        arenaManager.setEditingArena(player.getUniqueId(), arena);
        gui.open(player);
    }
}
