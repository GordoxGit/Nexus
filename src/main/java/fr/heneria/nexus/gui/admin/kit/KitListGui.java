package fr.heneria.nexus.gui.admin.kit;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import fr.heneria.nexus.game.kit.manager.KitManager;
import fr.heneria.nexus.game.kit.model.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Affiche la liste des kits disponibles et permet d'en créer de nouveaux.
 */
public class KitListGui {

    private final KitManager kitManager;
    private final AdminConversationManager conversationManager;

    public KitListGui(KitManager kitManager, AdminConversationManager conversationManager) {
        this.kitManager = kitManager;
        this.conversationManager = conversationManager;
    }

    public void open(Player player) {
        int kitCount = kitManager.getAllKits().size();
        int rows = Math.min(6, Math.max(1, (int) Math.ceil((kitCount + 2) / 9.0)));
        Gui gui = Gui.gui()
                .title(Component.text("Gestion des Kits"))
                .rows(rows)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        for (Kit kit : kitManager.getAllKits().values()) {
            GuiItem item = ItemBuilder.from(Material.CHEST)
                    .name(Component.text(kit.getName(), NamedTextColor.AQUA))
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        new KitEditorGui(kitManager, kit).open((Player) event.getWhoClicked());
                    });
            gui.addItem(item);
        }

        GuiItem create = ItemBuilder.from(Material.NETHER_STAR)
                .name(Component.text("Créer un nouveau kit", NamedTextColor.LIGHT_PURPLE))
                .lore(Component.text("Démarre une conversation"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    p.closeInventory();
                    conversationManager.startKitCreationConversation(p);
                });
        gui.addItem(create);

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    ((Player) event.getWhoClicked()).performCommand("nx admin");
                });
        gui.setItem(rows * 9 - 1, back);

        gui.getFiller().fill(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem());

        gui.open(player);
    }
}
