package fr.heneria.nexus.gui.player;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.game.model.MatchType;
import fr.heneria.nexus.game.queue.GameMode;
import fr.heneria.nexus.game.queue.QueueManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Interface permettant aux joueurs de sélectionner un mode de jeu et de rejoindre une file d'attente.
 */
public class GameModeSelectorGui {

    private final QueueManager queueManager;
    private final JavaPlugin plugin;

    public GameModeSelectorGui(QueueManager queueManager, JavaPlugin plugin) {
        this.queueManager = queueManager;
        this.plugin = plugin;
    }

    public void open(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("Choisissez votre expérience"))
                .rows(1)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        gui.addItem(ItemBuilder.from(Material.IRON_SWORD)
                .name(Component.text("Partie Normale", NamedTextColor.GREEN))
                .lore(Component.text("Mode de jeu standard", NamedTextColor.GRAY))
                .asGuiItem(e -> openModeMenu((Player) e.getWhoClicked(), MatchType.NORMAL)));

        gui.addItem(ItemBuilder.from(Material.DIAMOND_SWORD)
                .name(Component.text("Partie Classée", NamedTextColor.GREEN))
                .lore(Component.text("Affrontez les meilleurs", NamedTextColor.GRAY))
                .asGuiItem(e -> openModeMenu((Player) e.getWhoClicked(), MatchType.RANKED)));

        gui.getFiller().fill(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem());

        gui.open(player);
    }

    private void openModeMenu(Player player, MatchType type) {
        Gui gui = Gui.gui()
                .title(Component.text(type == MatchType.NORMAL ? "Partie Normale" : "Partie Classée"))
                .rows(1)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        for (GameMode mode : GameMode.values()) {
            gui.addItem(createItem(type, mode));
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (GameMode mode : GameMode.values()) {
                gui.updateItem(mode.ordinal(), createItem(type, mode));
            }
        }, 20L, 40L);

        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(e -> {
                    e.setCancelled(true);
                    task.cancel();
                    open((Player) e.getWhoClicked());
                });
        gui.setItem(8, back);

        gui.getFiller().fill(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem());

        gui.setCloseGuiAction(event -> task.cancel());
        gui.open(player);
    }

    private GuiItem createItem(MatchType type, GameMode mode) {
        Material material;
        switch (mode) {
            case TEAM_2V2:
                material = Material.DIAMOND_SWORD;
                break;
            case SOLO_1V1:
            default:
                material = Material.IRON_SWORD;
                break;
        }
        int size = queueManager.getQueueSize(type, mode);
        return ItemBuilder.from(material)
                .name(Component.text(mode.name()))
                .lore(Component.text(size + "/" + mode.getRequiredPlayers() + " joueurs"))
                .asGuiItem(event -> {
                    Player p = (Player) event.getWhoClicked();
                    QueueManager.QueueEntry entry = queueManager.getPlayerQueue(p.getUniqueId());
                    if (entry != null && entry.type() == type && entry.mode() == mode) {
                        queueManager.leaveQueue(p);
                    } else {
                        queueManager.joinQueue(p, type, mode);
                    }
                });
    }
}
