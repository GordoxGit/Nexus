package fr.heneria.nexus.gui.admin;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import fr.heneria.nexus.game.GameConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Interface d'administration des règles de jeu.
 */
public class GameRulesGui {

    public void open(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("Règles du Jeu"))
                .rows(3)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        GameConfig config = GameConfig.get();

        gui.setItem(10, createItem(Material.DIAMOND, "Manches pour gagner", String.valueOf(config.getRoundsToWin()), "rounds-to-win", false));
        gui.setItem(11, createItem(Material.NETHER_STAR, "Santé max du Nexus", String.valueOf(config.getNexusMaxHealth()), "nexus-max-health", true));
        gui.setItem(12, createItem(Material.REDSTONE, "Surcharges pour détruire", String.valueOf(config.getNexusSurchargesToDestroy()), "nexus-surcharges-to-destroy", false));

        gui.setItem(14, createItem(Material.BEACON, "Rayon capture cellule", String.valueOf(config.getEnergyCellCaptureRadius()), "energy-cell.capture-radius", true));
        gui.setItem(15, createItem(Material.CLOCK, "Temps capture cellule", String.valueOf(config.getEnergyCellCaptureTimeSeconds()), "energy-cell.capture-time-seconds", false));

        gui.setItem(19, createItem(Material.EMERALD, "Points début de manche", String.valueOf(config.getStartingRoundPoints()), "economy.starting-round-points", false));
        gui.setItem(20, createItem(Material.IRON_SWORD, "Récompense élimination", String.valueOf(config.getKillReward()), "economy.kill-reward", false));
        gui.setItem(21, createItem(Material.BOW, "Récompense assistance", String.valueOf(config.getAssistReward()), "economy.assist-reward", false));
        gui.setItem(22, createItem(Material.GOLD_INGOT, "Bonus manche gagnée", String.valueOf(config.getRoundWinBonus()), "economy.round-win-bonus", false));

        gui.open(player);
    }

    private GuiItem createItem(Material material, String name, String value, String key, boolean isDouble) {
        return ItemBuilder.from(material)
                .name(Component.text(name, NamedTextColor.GOLD))
                .lore(Component.text("Valeur actuelle: " + value))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    Player p = (Player) event.getWhoClicked();
                    AdminConversationManager.getInstance().startGameRuleConversation(p, key, isDouble, pl -> new GameRulesGui().open(pl));
                });
    }
}
