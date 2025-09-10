package fr.heneria.nexus.gui.admin.sanction;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import fr.heneria.nexus.sanction.SanctionManager;
import fr.heneria.nexus.sanction.model.Sanction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Affiche la liste des sanctions d'un joueur.
 */
public class SanctionListGui {

    private final UUID targetUuid;
    private final String playerName;
    private final SanctionManager sanctionManager;

    public SanctionListGui(UUID targetUuid, String playerName) {
        this.targetUuid = targetUuid;
        this.playerName = playerName;
        this.sanctionManager = SanctionManager.getInstance();
    }

    public void open(Player player) {
        PaginatedGui gui = Gui.paginated()
                .title(Component.text("Sanctions de : " + playerName))
                .rows(6)
                .pageSize(45)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        List<Sanction> sanctions = sanctionManager.getSanctions(targetUuid);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

        for (Sanction sanction : sanctions) {
            Material mat = sanction.isActive() ? Material.WRITABLE_BOOK : Material.BOOK;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Type: Abandon de partie", NamedTextColor.GRAY));
            if (sanction.getSanctionDate() != null) {
                lore.add(Component.text("Date: " + formatter.format(sanction.getSanctionDate()), NamedTextColor.GRAY));
            }
            if (!sanction.isActive()) {
                lore.add(Component.text("Expire le: Pardonné", NamedTextColor.GRAY));
            } else if (sanction.getExpirationTime() != null) {
                lore.add(Component.text("Expire le: " + formatter.format(sanction.getExpirationTime()), NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("Expire le: Permanent", NamedTextColor.GRAY));
            }
            lore.add(Component.text("Statut: " + (sanction.isActive() ? "Actif" : "Inactif"),
                    sanction.isActive() ? NamedTextColor.GREEN : NamedTextColor.GRAY));

            ItemBuilder builder = ItemBuilder.from(mat)
                    .name(Component.text("Sanction #" + sanction.getId(), NamedTextColor.YELLOW))
                    .lore(lore);

            GuiItem item;
            if (sanction.isActive()) {
                item = builder.asGuiItem(event -> {
                    event.setCancelled(true);
                    new SanctionPardonConfirmGui(targetUuid, playerName).open((Player) event.getWhoClicked());
                });
            } else {
                item = builder.asGuiItem(event -> event.setCancelled(true));
            }
            gui.addItem(item);
        }

        GuiItem prev = ItemBuilder.from(Material.ARROW)
                .name(Component.text("Page précédente", NamedTextColor.YELLOW))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    gui.previous();
                });
        GuiItem next = ItemBuilder.from(Material.ARROW)
                .name(Component.text("Page suivante", NamedTextColor.YELLOW))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    gui.next();
                });
        GuiItem back = ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Retour", NamedTextColor.RED))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    new SanctionSearchGui().open((Player) event.getWhoClicked());
                });

        gui.setItem(45, prev);
        gui.setItem(49, back);
        gui.setItem(53, next);
        gui.getFiller().fillBottom(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem());

        gui.open(player);
    }
}
