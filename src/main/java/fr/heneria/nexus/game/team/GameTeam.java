package fr.heneria.nexus.game.team;

import net.kyori.adventure.text.format.NamedTextColor;
import lombok.Getter;
import org.bukkit.Material;

@Getter
public enum GameTeam {
    BLUE("Bleu", NamedTextColor.BLUE, Material.BLUE_WOOL),
    RED("Rouge", NamedTextColor.RED, Material.RED_WOOL);

    private final String name;
    private final NamedTextColor color;
    private final Material material;

    GameTeam(String name, NamedTextColor color, Material material) {
        this.name = name;
        this.color = color;
        this.material = material;
    }
}
