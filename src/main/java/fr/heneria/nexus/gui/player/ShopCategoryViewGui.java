package fr.heneria.nexus.gui.player;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.player.manager.PlayerManager;
import fr.heneria.nexus.player.model.PlayerProfile;
import fr.heneria.nexus.shop.manager.ShopManager;
import fr.heneria.nexus.shop.model.ShopItem;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.scoreboard.ScoreboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;

import java.util.List;

/**
 * Vue détaillée d'une catégorie de la boutique.
 */
public class ShopCategoryViewGui {

    private final ShopManager shopManager;
    private final PlayerManager playerManager;
    private final JavaPlugin plugin;
    private final Match match;
    private final String category;

    public ShopCategoryViewGui(ShopManager shopManager, PlayerManager playerManager,
                               JavaPlugin plugin, Match match, String category) {
        this.shopManager = shopManager;
        this.playerManager = playerManager;
        this.plugin = plugin;
        this.match = match;
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
                        int points = match.getRoundPoints().getOrDefault(p.getUniqueId(), 0);
                        if (points < item.getPrice()) {
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            p.sendMessage("§cVous n'avez pas assez de points !");
                            return;
                        }
                        match.getRoundPoints().put(p.getUniqueId(), points - item.getPrice());
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            p.getInventory().addItem(new ItemStack(item.getMaterial()));
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                            ScoreboardManager.getInstance().updateScoreboard(match);
                        });
                    });
            gui.addItem(guiItem);
        }

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ShopGui(shopManager, playerManager, plugin, match).open((Player) event.getWhoClicked());
                });
        gui.setItem(rows * 9 - 1, back);

        gui.getFiller().fill(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem());

        gui.open(player);
    }
}
