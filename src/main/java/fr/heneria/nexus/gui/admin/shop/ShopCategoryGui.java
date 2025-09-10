package fr.heneria.nexus.gui.admin.shop;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import fr.heneria.nexus.shop.manager.ShopManager;
import fr.heneria.nexus.shop.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import fr.heneria.nexus.utils.Theme;

import java.util.List;

/**
 * Interface d'édition des items d'une catégorie de boutique.
 */
public class ShopCategoryGui {

    private final ShopManager shopManager;
    private final String category;

    public ShopCategoryGui(ShopManager shopManager, String category) {
        this.shopManager = shopManager;
        this.category = category;
    }

    public void open(Player player) {
        List<ShopItem> items = shopManager.getItemsForCategory(category);
        int rows = Math.min(6, Math.max(1, (int) Math.ceil(items.size() / 9.0)));

        Gui gui = Gui.gui()
                .title(Component.text("Boutique - " + category, Theme.COLOR_PRIMARY))
                .rows(rows)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        for (ShopItem item : items) {
            GuiItem guiItem = ItemBuilder.from(item.getMaterial())
                    .name(Component.text(item.getDisplayName() != null ? item.getDisplayName() : item.getMaterial().name()))
                    .lore(Component.text("Prix: " + item.getPrice() + " points", NamedTextColor.YELLOW))
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        Player p = (Player) event.getWhoClicked();
                        p.closeInventory();
                        AdminConversationManager.getInstance().startPriceConversation(p, item);
                    });
            gui.addItem(guiItem);
        }

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", Theme.COLOR_ERROR))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ShopAdminGui(shopManager).open((Player) event.getWhoClicked());
                });

        gui.setItem(rows * 9 - 1, back);

        gui.getFiller().fill(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem());

        gui.open(player);
    }
}
