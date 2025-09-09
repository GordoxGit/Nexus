package fr.heneria.nexus.gui.admin;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import fr.heneria.nexus.admin.placement.GameObjectPlacementContext;
import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.arena.model.ArenaGameObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * GUI pour gérer les objets de jeu d'une arène.
 */
public class ArenaGameObjectGui {

    private final ArenaManager arenaManager;
    private final Arena arena;
    private final AdminPlacementManager adminPlacementManager;

    public ArenaGameObjectGui(ArenaManager arenaManager, Arena arena, AdminPlacementManager adminPlacementManager) {
        this.arenaManager = arenaManager;
        this.arena = arena;
        this.adminPlacementManager = adminPlacementManager;
    }

    public void open(Player player) {
        Component title = Component.text("Objets de Jeu: ", NamedTextColor.GOLD)
                .append(Component.text(arena.getName(), NamedTextColor.YELLOW));

        Gui gui = Gui.gui()
                .title(title)
                .rows(1)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.setCloseGuiAction(event -> {});

        ArenaGameObject cell = arena.getGameObject("ENERGY_CELL", 1).orElseGet(() -> {
            ArenaGameObject obj = new ArenaGameObject("ENERGY_CELL", 1);
            arena.getGameObjects().add(obj);
            return obj;
        });

        GuiItem energyCellItem = ItemBuilder.from(Material.BEACON)
                .name(Component.text("Cellule d'Énergie 1", NamedTextColor.AQUA))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    p.closeInventory();
                    adminPlacementManager.startPlacementMode(p, new GameObjectPlacementContext(arena, cell));
                });

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ArenaEditorGui(arenaManager, arena, adminPlacementManager).open((Player) event.getWhoClicked());
                });

        gui.setItem(0, energyCellItem);

        for (int i = 1; i <= arena.getMaxPlayers() && i < 8; i++) {
            final int teamId = i;
            ArenaGameObject core = arena.getGameObject("NEXUS_CORE", teamId).orElseGet(() -> {
                ArenaGameObject obj = new ArenaGameObject("NEXUS_CORE", teamId);
                arena.getGameObjects().add(obj);
                return obj;
            });
            GuiItem coreItem = ItemBuilder.from(Material.END_CRYSTAL)
                    .name(Component.text("Cœur Nexus - Équipe " + teamId, NamedTextColor.LIGHT_PURPLE))
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        Player p = (Player) event.getWhoClicked();
                        p.closeInventory();
                        adminPlacementManager.startPlacementMode(p, new GameObjectPlacementContext(arena, core));
                    });
            gui.setItem(i, coreItem);
        }

        gui.setItem(8, back);

        gui.open(player);
    }
}
