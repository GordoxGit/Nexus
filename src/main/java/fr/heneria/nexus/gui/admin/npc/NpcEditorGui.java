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
 * GUI for editing a single NPC.
 */
public class NpcEditorGui {

    private final NpcManager npcManager;
    private final Npc npc;
    private final AdminConversationManager conversationManager;
    private final NpcListGui parent;

    public NpcEditorGui(NpcManager npcManager, Npc npc, AdminConversationManager conversationManager, NpcListGui parent) {
        this.npcManager = npcManager;
        this.npc = npc;
        this.conversationManager = conversationManager;
        this.parent = parent;
    }

    public void open(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("Édition PNJ"))
                .rows(3)
                .create();
        gui.setDefaultClickAction(e -> e.setCancelled(true));

        GuiItem rename = ItemBuilder.from(Material.NAME_TAG)
                .name(Component.text("Modifier le nom", NamedTextColor.YELLOW))
                .asGuiItem(e -> {
                    e.setCancelled(true);
                    e.getWhoClicked().sendMessage("TODO: Modification du nom non implémentée");
                });
        gui.setItem(10, rename);

        GuiItem command = ItemBuilder.from(Material.COMMAND_BLOCK)
                .name(Component.text("Modifier la commande", NamedTextColor.GOLD))
                .asGuiItem(e -> {
                    e.setCancelled(true);
                    e.getWhoClicked().sendMessage("TODO: Modification de la commande non implémentée");
                });
        gui.setItem(12, command);

        GuiItem place = ItemBuilder.from(Material.ENDER_PEARL)
                .name(Component.text("Placer/Déplacer le PNJ", NamedTextColor.GREEN))
                .asGuiItem(e -> {
                    e.setCancelled(true);
                    Player p = (Player) e.getWhoClicked();
                    p.closeInventory();
                    npcManager.placeNpc(npc, p);
                    parent.open(p);
                });
        gui.setItem(14, place);

        GuiItem delete = ItemBuilder.from(Material.LAVA_BUCKET)
                .name(Component.text("Supprimer", NamedTextColor.RED))
                .asGuiItem(e -> {
                    e.setCancelled(true);
                    Player p = (Player) e.getWhoClicked();
                    npcManager.deleteNpc(npc);
                    parent.open(p);
                });
        gui.setItem(16, delete);

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(e -> {
                    e.setCancelled(true);
                    parent.open((Player) e.getWhoClicked());
                });
        gui.setItem(26, back);

        gui.getFiller().fill(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem());

        gui.open(player);
    }
}

