package fr.heneria.nexus.gui.admin;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import fr.heneria.nexus.utils.Theme;

/**
 * GUI de confirmation de suppression d'une arène.
 */
public class ConfirmDeleteGui {

    private final ArenaManager arenaManager;
    private final Arena arena;
    private final AdminPlacementManager adminPlacementManager;

    public ConfirmDeleteGui(ArenaManager arenaManager, Arena arena, AdminPlacementManager adminPlacementManager) {
        this.arenaManager = arenaManager;
        this.arena = arena;
        this.adminPlacementManager = adminPlacementManager;
    }

    public void open(Player player) {
        Component title = Component.text("Confirmer la suppression de ", NamedTextColor.DARK_RED)
                .append(Component.text(arena.getName(), NamedTextColor.RED))
                .append(Component.text(" ?", NamedTextColor.DARK_RED));

        Gui gui = Gui.gui()
                .title(title)
                .rows(3)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.setCloseGuiAction(event -> {});

        GuiItem yes = ItemBuilder.from(Material.LIME_CONCRETE)
                .name(Component.text("OUI, SUPPRIMER", Theme.COLOR_SUCCESS))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    arenaManager.deleteArena(arena);
                    arenaManager.stopEditing(p.getUniqueId());
                    new ArenaListGui(arenaManager, AdminConversationManager.getInstance(), adminPlacementManager).open(p);
                });

        GuiItem no = ItemBuilder.from(Material.RED_CONCRETE)
                .name(Component.text("NON, ANNULER", Theme.COLOR_ERROR))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ArenaEditorGui(arenaManager, arena, adminPlacementManager).open((Player) event.getWhoClicked());
                });

        gui.setItem(12, yes);
        gui.setItem(14, no);

        gui.getFiller().fill(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem());

        gui.open(player);
    }
}

