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
import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import fr.heneria.nexus.shop.manager.ShopManager;
import fr.heneria.nexus.game.kit.manager.KitManager;
import fr.heneria.nexus.gui.admin.kit.KitListGui;
import fr.heneria.nexus.gui.admin.shop.ShopAdminGui;
import fr.heneria.nexus.gui.admin.npc.NpcListGui;
import fr.heneria.nexus.gui.admin.sanction.SanctionSearchGui;
import fr.heneria.nexus.npc.NpcManager;

/**
 * Menu principal du centre de contrôle Nexus.
 */
public class AdminMenuGui {

    private final ArenaManager arenaManager;
    private final AdminPlacementManager adminPlacementManager;
    private final ShopManager shopManager;
    private final KitManager kitManager;
    private final NpcManager npcManager;

    public AdminMenuGui(ArenaManager arenaManager, AdminPlacementManager adminPlacementManager, ShopManager shopManager, KitManager kitManager, NpcManager npcManager) {
        this.arenaManager = arenaManager;
        this.adminPlacementManager = adminPlacementManager;
        this.shopManager = shopManager;
        this.kitManager = kitManager;
        this.npcManager = npcManager;
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
                    new ArenaListGui(arenaManager, AdminConversationManager.getInstance(), adminPlacementManager).open((Player) event.getWhoClicked());
                });

        // Place l'item au centre
        gui.setItem(13, arenaManagement);

        GuiItem rulesManagement = ItemBuilder.from(Material.COMMAND_BLOCK)
                .name(Component.text("Règles du Jeu", NamedTextColor.GOLD))
                .lore(Component.text("Modifier les paramètres de gameplay"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new GameRulesGui().open((Player) event.getWhoClicked());
                });
        gui.setItem(11, rulesManagement);

        GuiItem shopManagement = ItemBuilder.from(Material.CHEST)
                .name(Component.text("Gestion de la Boutique", NamedTextColor.GREEN))
                .lore(Component.text("Configurer la boutique en jeu"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ShopAdminGui(shopManager).open((Player) event.getWhoClicked());
                });
        gui.setItem(15, shopManagement);

        GuiItem kitManagement = ItemBuilder.from(Material.IRON_CHESTPLATE)
                .name(Component.text("Gestion des Kits", NamedTextColor.LIGHT_PURPLE))
                .lore(Component.text("Créer et modifier les kits"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new KitListGui(kitManager, AdminConversationManager.getInstance()).open((Player) event.getWhoClicked());
                });
        gui.setItem(17, kitManagement);

        GuiItem npcManagement = ItemBuilder.from(Material.VILLAGER_SPAWN_EGG)
                .name(Component.text("Gestion des PNJ", NamedTextColor.AQUA))
                .lore(Component.text("Créer et placer des PNJ"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new NpcListGui(npcManager, AdminConversationManager.getInstance()).open((Player) event.getWhoClicked());
                });
        gui.setItem(9, npcManagement);

        GuiItem sanctionManagement = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Gestion des Sanctions", NamedTextColor.RED))
                .lore(Component.text("Voir et pardonner les sanctions"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new SanctionSearchGui().open((Player) event.getWhoClicked());
                });
        gui.setItem(22, sanctionManagement);
        gui.open(player);
    }
}
