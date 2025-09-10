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

import java.util.Map;

/**
 * Interface listant toutes les arènes chargées.
 */
public class ArenaListGui {

    private final ArenaManager arenaManager;
    private final AdminConversationManager adminConversationManager;
    private final AdminPlacementManager adminPlacementManager;

    public ArenaListGui(ArenaManager arenaManager, AdminConversationManager adminConversationManager, AdminPlacementManager adminPlacementManager) {
        this.arenaManager = arenaManager;
        this.adminConversationManager = adminConversationManager;
        this.adminPlacementManager = adminPlacementManager;
    }

    /**
     * Ouvre l'interface listant les arènes.
     *
     * @param player joueur à qui ouvrir l'interface
     */
    public void open(Player player) {
        int arenaCount = arenaManager.getAllArenas().size();
        int rows = Math.min(6, Math.max(1, (int) Math.ceil((arenaCount + 1) / 9.0)));

        Gui gui = Gui.gui()
                .title(Component.text("Gestion des Arènes", Theme.COLOR_PRIMARY))
                .rows(rows)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        for (Arena arena : arenaManager.getAllArenas()) {
            int spawnCount = arena.getSpawns().values().stream().mapToInt(Map::size).sum();
            GuiItem item = ItemBuilder.from(Material.PAPER)
                    .name(Component.text(arena.getName()))
                    .lore(
                            Component.text("Joueurs max: " + arena.getMaxPlayers()),
                            Component.text("Spawns définis: " + spawnCount)
                    )
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        new ArenaEditorGui(arenaManager, arena, adminPlacementManager).open((Player) event.getWhoClicked());
                    });
            gui.addItem(item);
        }

        GuiItem create = ItemBuilder.from(Material.NETHER_STAR)
                .name(Component.text("Créer une nouvelle arène", Theme.COLOR_PRIMARY))
                .lore(Component.text("Démarre une conversation"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    p.closeInventory();
                    adminConversationManager.startConversation(p);
                });
        gui.addItem(create);

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", Theme.COLOR_ERROR))
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
