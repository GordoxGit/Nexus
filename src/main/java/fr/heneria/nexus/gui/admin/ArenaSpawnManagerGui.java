package fr.heneria.nexus.gui.admin;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.gui.admin.ArenaEditorGui;
import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import fr.heneria.nexus.admin.placement.SpawnPlacementContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;

/**
 * GUI permettant de visualiser et de gérer les spawns d'une arène.
 */
public class ArenaSpawnManagerGui {

    private final ArenaManager arenaManager;
    private final Arena arena;
    private final AdminPlacementManager adminPlacementManager;

    public ArenaSpawnManagerGui(ArenaManager arenaManager, Arena arena, AdminPlacementManager adminPlacementManager) {
        this.arenaManager = arenaManager;
        this.arena = arena;
        this.adminPlacementManager = adminPlacementManager;
    }

    public void open(Player player) {
        Component title = Component.text("Gestion des Spawns: ", NamedTextColor.GOLD)
                .append(Component.text(arena.getName(), NamedTextColor.YELLOW));

        int playersPerTeam = Math.max(1, arena.getMaxPlayers() / 2);
        int totalSpawns = playersPerTeam * 2;
        int rows = Math.min(5, Math.max(4, (int) Math.ceil((totalSpawns + 1) / 9.0)));

        Gui gui = Gui.gui()
                .title(title)
                .rows(rows)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.setCloseGuiAction(event -> {});

        for (int teamId = 1; teamId <= 2; teamId++) {
            for (int spawn = 1; spawn <= playersPerTeam; spawn++) {
                Map<Integer, Location> teamSpawns = arena.getSpawns().getOrDefault(teamId, Collections.emptyMap());
                Location loc = teamSpawns.get(spawn);

                ItemBuilder builder = ItemBuilder.from(loc == null ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE)
                        .name(Component.text("Équipe " + teamId + " - Spawn " + spawn, NamedTextColor.YELLOW));

                if (loc == null) {
                    builder.lore(Component.text("Statut: Non défini", NamedTextColor.RED));
                } else {
                    builder.lore(
                            Component.text("Statut: Défini", NamedTextColor.GREEN),
                            Component.text("Monde: " + loc.getWorld().getName(), NamedTextColor.GRAY),
                            Component.text(String.format("X: %.2f, Y: %.2f, Z: %.2f", loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GRAY)
                    );
                }

                int finalTeamId = teamId;
                int finalSpawn = spawn;
                GuiItem item = builder.asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    p.closeInventory();
                    SpawnPlacementContext context = new SpawnPlacementContext(arena, finalTeamId, finalSpawn);
                    adminPlacementManager.startPlacementMode(p, context);
                });

                gui.addItem(item);
            }
        }

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new ArenaEditorGui(arenaManager, arena, adminPlacementManager).open((Player) event.getWhoClicked());
                });

        gui.setItem(rows * 9 - 1, back);

        gui.open(player);
    }
}

