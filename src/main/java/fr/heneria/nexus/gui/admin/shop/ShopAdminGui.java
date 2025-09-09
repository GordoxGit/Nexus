package fr.heneria.nexus.gui.admin.shop;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.shop.manager.ShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Interface d'administration de la boutique.
 */
public class ShopAdminGui {

    private final ShopManager shopManager;

    public ShopAdminGui(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    public void open(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("Gestion de la Boutique"))
                .rows(3)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        GuiItem armes = ItemBuilder.from(Material.DIAMOND_SWORD)
                .name(Component.text("Armes", NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ShopCategoryGui(shopManager, "Armes").open((Player) event.getWhoClicked());
                });
        GuiItem armures = ItemBuilder.from(Material.DIAMOND_CHESTPLATE)
                .name(Component.text("Armures", NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ShopCategoryGui(shopManager, "Armures").open((Player) event.getWhoClicked());
                });
        GuiItem utilitaires = ItemBuilder.from(Material.POTION)
                .name(Component.text("Utilitaires", NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ShopCategoryGui(shopManager, "Utilitaires").open((Player) event.getWhoClicked());
                });

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    ((Player) event.getWhoClicked()).performCommand("nx admin");
                });

        gui.addItem(armes, armures, utilitaires);
        gui.setItem(26, back);

        gui.getFiller().fill(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem());

        gui.open(player);
    }
}
