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
import org.bukkit.inventory.ItemStack;

/**
 * GUI d'édition d'un kit, permettant de modifier l'inventaire complet.
 */
public class KitEditorGui {

    private final KitManager kitManager;
    private final Kit kit;

    public KitEditorGui(KitManager kitManager, Kit kit) {
        this.kitManager = kitManager;
        this.kit = kit;
    }

    public void open(Player player) {
        Component title = Component.text("Édition du Kit : ", NamedTextColor.DARK_GRAY)
                .append(Component.text(kit.getName(), NamedTextColor.LIGHT_PURPLE));
        Gui gui = Gui.gui()
                .title(title)
                .rows(6)
                .create();

        // Autoriser les interactions par défaut
        gui.setDefaultClickAction(event -> {});

        gui.getInventory().setContents(kit.getContents());

        GuiItem save = ItemBuilder.from(Material.EMERALD_BLOCK)
                .name(Component.text("Sauvegarder", NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    ItemStack[] contents = gui.getInventory().getContents();
                    kit.setContents(contents);
                    kitManager.saveKit(kit);
                    player.sendMessage("§aKit sauvegardé.");
                });
        gui.setItem(53, save);

        GuiItem delete = ItemBuilder.from(Material.TNT)
                .name(Component.text("Supprimer", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ConfirmDeleteKitGui(kitManager, kit).open((Player) event.getWhoClicked());
                });
        gui.setItem(52, delete);

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new KitListGui(kitManager, fr.heneria.nexus.admin.conversation.AdminConversationManager.getInstance()).open((Player) event.getWhoClicked());
                });
        gui.setItem(45, back);

        gui.open(player);
    }
}
