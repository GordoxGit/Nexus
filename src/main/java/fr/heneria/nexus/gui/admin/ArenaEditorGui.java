package fr.heneria.nexus.gui.admin;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Interface d'édition d'une arène spécifique.
 */
public class ArenaEditorGui {

    private final ArenaManager arenaManager;
    private final Arena arena;

    public ArenaEditorGui(ArenaManager arenaManager, Arena arena) {
        this.arenaManager = arenaManager;
        this.arena = arena;
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

                    // CORRECTION: On utilise la variable 'gui' qui est dans le scope
                    gui.setCloseGuiAction(closeEvent -> {
                    }); // On désactive temporairement l'action de fermeture pour ne pas enlever le joueur du mode édition

                    p.closeInventory();
                    p.sendMessage("§6[Nexus] §fMode édition des spawns pour l'arène '§e" + arena.getName() + "§f'.");
                    p.sendMessage("§71. Placez-vous à l'emplacement souhaité.");
                    p.sendMessage("§72. Tapez la commande: §c/nx setspawn <teamId> <spawnNum>");
                    p.sendMessage("§7Exemple: §c/nx setspawn 1 1 §7pour le premier spawn de l'équipe 1.");
                });

        GuiItem save = ItemBuilder.from(Material.ANVIL)
                .name(Component.text("Sauvegarder l'arène", NamedTextColor.DARK_GREEN))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    arenaManager.saveArena(arena);
                    ((Player) event.getWhoClicked()).sendMessage("§aArène '" + arena.getName() + "' sauvegardée avec succès !");
                });

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ArenaListGui(arenaManager, AdminConversationManager.getInstance()).open((Player) event.getWhoClicked());
                });

        gui.setItem(13, info);
        gui.setItem(11, setSpawns);
        gui.setItem(15, save);
        gui.setItem(26, back);

        arenaManager.setEditingArena(player.getUniqueId(), arena);
        gui.open(player);
    }
}
