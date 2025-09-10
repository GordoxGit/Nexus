package fr.heneria.nexus.gui.admin.sanction;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.sanction.SanctionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * GUI de confirmation pour le pardon d'une sanction.
 */
public class SanctionPardonConfirmGui {

    private final UUID targetUuid;
    private final String playerName;

    public SanctionPardonConfirmGui(UUID targetUuid, String playerName) {
        this.targetUuid = targetUuid;
        this.playerName = playerName;
    }

    public void open(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("Pardonner la sanction ?", NamedTextColor.RED))
                .rows(3)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        GuiItem yes = ItemBuilder.from(Material.LIME_CONCRETE)
                .name(Component.text("OUI, PARDONNER", NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    SanctionManager.getInstance().pardonLastPenalty(targetUuid);
                    new SanctionListGui(targetUuid, playerName).open((Player) event.getWhoClicked());
                });

        GuiItem no = ItemBuilder.from(Material.RED_CONCRETE)
                .name(Component.text("NON, ANNULER", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new SanctionListGui(targetUuid, playerName).open((Player) event.getWhoClicked());
                });

        gui.setItem(12, yes);
        gui.setItem(14, no);
        gui.open(player);
    }
}
