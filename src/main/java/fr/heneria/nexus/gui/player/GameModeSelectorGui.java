package fr.heneria.nexus.gui.player;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.game.queue.GameMode;
import fr.heneria.nexus.game.queue.QueueManager;
import net.kyori.adventure.text.Component;
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
                .title(Component.text("Sélection du mode"))
                .rows(1)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        for (GameMode mode : GameMode.values()) {
            gui.addItem(createItem(mode));
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (GameMode mode : GameMode.values()) {
                gui.updateItem(mode.ordinal(), createItem(mode));
            }
        }, 20L, 40L);

        gui.setCloseGuiAction(event -> task.cancel());
        gui.open(player);
    }

    private GuiItem createItem(GameMode mode) {
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
        int size = queueManager.getQueueSize(mode);
        return ItemBuilder.from(material)
                .name(Component.text(mode.name()))
                .lore(Component.text(size + "/" + mode.getRequiredPlayers() + " joueurs"))
                .asGuiItem(event -> {
                    Player p = (Player) event.getWhoClicked();
                    if (queueManager.getPlayerQueue(p.getUniqueId()) == mode) {
                        queueManager.leaveQueue(p);
                    } else {
                        queueManager.joinQueue(p, mode);
                    }
                });
    }
}
