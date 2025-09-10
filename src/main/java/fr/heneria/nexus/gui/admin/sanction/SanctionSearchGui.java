package fr.heneria.nexus.gui.admin.sanction;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Interface de recherche d'un joueur pour la gestion des sanctions.
 */
public class SanctionSearchGui {

    public void open(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("Rechercher un joueur"))
                .rows(1)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        GuiItem search = ItemBuilder.from(Material.PLAYER_HEAD)
                .name(Component.text("Entrer le nom du joueur", NamedTextColor.AQUA))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    p.closeInventory();
                    AdminConversationManager.getInstance().startSanctionSearchConversation(p);
                });

        gui.setItem(4, search);
        gui.open(player);
    }
}
