package fr.heneria.nexus.gui.admin;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Interface listant toutes les arènes chargées.
 */
public class ArenaListGui {

    private final ArenaManager arenaManager;

    public ArenaListGui(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
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
                .title(Component.text("Gestion des Arènes"))
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
                        new ArenaEditorGui(arenaManager, arena).open((Player) event.getWhoClicked());
                    });
            gui.addItem(item);
        }

        GuiItem create = ItemBuilder.from(Material.NETHER_STAR)
                .name(Component.text("Créer une nouvelle arène", NamedTextColor.AQUA))
                .lore(Component.text("Fonctionnalité à venir"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    ((Player) event.getWhoClicked()).sendMessage("Utilisez /nx arena create <nom> <joueurs>");
                });
        gui.addItem(create);

        gui.open(player);
    }
}
