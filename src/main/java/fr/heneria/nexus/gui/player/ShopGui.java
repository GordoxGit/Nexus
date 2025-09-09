package fr.heneria.nexus.gui.player;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.player.manager.PlayerManager;
import fr.heneria.nexus.shop.manager.ShopManager;
import fr.heneria.nexus.shop.model.ShopItem;
import fr.heneria.nexus.game.model.Match;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

/**
 * Interface de boutique pour les joueurs.
 */
public class ShopGui {

    private final ShopManager shopManager;
    private final PlayerManager playerManager;
    private final JavaPlugin plugin;
    private final Match match;

    public ShopGui(ShopManager shopManager, PlayerManager playerManager, JavaPlugin plugin, Match match) {
        this.shopManager = shopManager;
        this.playerManager = playerManager;
        this.plugin = plugin;
        this.match = match;
    }

    public void open(Player player) {
        Set<String> categories = shopManager.getCategories();
        int rows = Math.min(6, Math.max(1, (int) Math.ceil(categories.size() / 9.0)));
        Gui gui = Gui.gui()
                .title(Component.text("Boutique"))
                .rows(rows)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        for (String category : categories) {
            List<ShopItem> items = shopManager.getItemsForCategory(category);
            Material icon = items.isEmpty() ? Material.CHEST : items.get(0).getMaterial();
            GuiItem guiItem = ItemBuilder.from(icon)
                    .name(Component.text(category, NamedTextColor.GREEN))
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        new ShopCategoryViewGui(shopManager, playerManager, plugin, match, category)
                                .open((Player) event.getWhoClicked());
                    });
            gui.addItem(guiItem);
        }

        gui.open(player);
    }
}
