package fr.heneria.nexus.gui.admin.kit;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.game.kit.manager.KitManager;
import fr.heneria.nexus.game.kit.model.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * GUI de confirmation de suppression d'un kit.
 */
public class ConfirmDeleteKitGui {

    private final KitManager kitManager;
    private final Kit kit;

    public ConfirmDeleteKitGui(KitManager kitManager, Kit kit) {
        this.kitManager = kitManager;
        this.kit = kit;
    }

    public void open(Player player) {
        Component title = Component.text("Supprimer ", NamedTextColor.DARK_RED)
                .append(Component.text(kit.getName(), NamedTextColor.RED))
                .append(Component.text(" ?", NamedTextColor.DARK_RED));
        Gui gui = Gui.gui()
                .title(title)
                .rows(3)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        GuiItem yes = ItemBuilder.from(Material.LIME_CONCRETE)
                .name(Component.text("OUI, SUPPRIMER", NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    kitManager.deleteKit(kit.getName());
                    new KitListGui(kitManager, fr.heneria.nexus.admin.conversation.AdminConversationManager.getInstance()).open((Player) event.getWhoClicked());
                });
        GuiItem no = ItemBuilder.from(Material.RED_CONCRETE)
                .name(Component.text("NON, ANNULER", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new KitEditorGui(kitManager, kit).open((Player) event.getWhoClicked());
                });

        gui.setItem(12, yes);
        gui.setItem(14, no);
        gui.open(player);
    }
}
