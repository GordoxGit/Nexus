package fr.heneria.nexus.gui.player;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.economy.manager.EconomyManager;
import fr.heneria.nexus.economy.model.TransactionType;
import fr.heneria.nexus.player.manager.PlayerManager;
import fr.heneria.nexus.player.model.PlayerProfile;
import fr.heneria.nexus.shop.manager.ShopManager;
import fr.heneria.nexus.shop.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Vue détaillée d'une catégorie de la boutique.
 */
public class ShopCategoryViewGui {

    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final PlayerManager playerManager;
    private final JavaPlugin plugin;
    private final String category;

    public ShopCategoryViewGui(ShopManager shopManager, EconomyManager economyManager, PlayerManager playerManager,
                               JavaPlugin plugin, String category) {
        this.shopManager = shopManager;
        this.economyManager = economyManager;
        this.playerManager = playerManager;
        this.plugin = plugin;
        this.category = category;
    }

    public void open(Player player) {
        List<ShopItem> items = shopManager.getItemsForCategory(category);
        int rows = Math.min(6, Math.max(1, (int) Math.ceil(items.size() / 9.0)));
        Gui gui = Gui.gui()
                .title(Component.text("Boutique - " + category))
                .rows(rows)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        for (ShopItem item : items) {
            if (!item.isEnabled()) {
                continue;
            }
            GuiItem guiItem = ItemBuilder.from(item.getMaterial())
                    .name(Component.text(item.getDisplayName() != null ? item.getDisplayName() : item.getMaterial().name()))
                    .lore(Component.text("Prix: " + item.getPrice() + " points", NamedTextColor.YELLOW))
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        Player p = (Player) event.getWhoClicked();
                        PlayerProfile profile = playerManager.getPlayerProfile(p.getUniqueId());
                        if (profile == null) {
                            return;
                        }
                        if (!economyManager.hasEnoughPoints(p.getUniqueId(), item.getPrice())) {
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            p.sendMessage("§cVous n'avez pas assez de points !");
                            return;
                        }
                        economyManager.removePoints(p.getUniqueId(), item.getPrice(), TransactionType.SPEND_SHOP, "shop")
                                .thenAccept(success -> {
                                    if (success) {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            p.getInventory().addItem(new ItemStack(item.getMaterial()));
                                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                                        });
                                    }
                                });
                    });
            gui.addItem(guiItem);
        }

        gui.open(player);
    }
}
