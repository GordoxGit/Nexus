package fr.heneria.nexus.gui.admin.npc;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import fr.heneria.nexus.npc.NpcManager;
import fr.heneria.nexus.npc.model.Npc;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * GUI listing NPCs and allowing creation of new ones.
 */
public class NpcListGui {

    private final NpcManager npcManager;
    private final AdminConversationManager conversationManager;

    public NpcListGui(NpcManager npcManager, AdminConversationManager conversationManager) {
        this.npcManager = npcManager;
        this.conversationManager = conversationManager;
    }

    public void open(Player player) {
        int count = npcManager.getNpcs().size();
        int rows = Math.min(6, Math.max(1, (int) Math.ceil((count + 2) / 9.0)));
        Gui gui = Gui.gui()
                .title(Component.text("Gestion des PNJ"))
                .rows(rows)
                .create();
        gui.setDefaultClickAction(e -> e.setCancelled(true));

        for (Npc npc : npcManager.getNpcs().values()) {
            GuiItem item = ItemBuilder.from(Material.PLAYER_HEAD)
                    .name(Component.text(npc.getName(), NamedTextColor.AQUA))
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        new NpcEditorGui(npcManager, npc, conversationManager, this).open((Player) event.getWhoClicked());
                    });
            gui.addItem(item);
        }

        GuiItem create = ItemBuilder.from(Material.NETHER_STAR)
                .name(Component.text("Créer un PNJ", NamedTextColor.LIGHT_PURPLE))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    p.closeInventory();
                    conversationManager.startNpcCreationConversation(p, this, npcManager);
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

