package fr.heneria.nexus.gui.admin;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.arena.manager.ArenaManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;

/**
 * Menu principal du centre de contrôle Nexus.
 */
public class AdminMenuGui {

    private final ArenaManager arenaManager;

    public AdminMenuGui(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    /**
     * Ouvre le GUI du menu principal pour le joueur donné.
     *
     * @param player joueur à qui ouvrir l'interface
     */
    public void open(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("Centre de Contrôle Nexus"))
                .rows(3)
                .create();

        // Empêche la récupération des items
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        GuiItem arenaManagement = ItemBuilder.from(Material.GRASS_BLOCK)
                .name(Component.text("Gestion des Arènes", NamedTextColor.GREEN))
                .lore(Component.text("Configurer et gérer les arènes"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ArenaListGui(arenaManager, AdminConversationManager.getInstance()).open((Player) event.getWhoClicked());
                });

        // Place l'item au centre
        gui.setItem(13, arenaManagement);
        gui.open(player);
    }
}
