package fr.heneria.nexus.game.model;

import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Couleurs centralisées des équipes pour une cohérence globale.
 */
public final class TeamColor {

    private static final Map<Integer, NamedTextColor> COLORS = Map.of(
            1, NamedTextColor.BLUE,
            2, NamedTextColor.RED
    );

    private TeamColor() {
    }

    /**
     * Récupère la couleur {@link NamedTextColor} associée à l'identifiant d'équipe donné.
     *
     * @param teamId identifiant de l'équipe
     * @return couleur de l'équipe ou blanc par défaut
     */
    public static NamedTextColor of(int teamId) {
        return COLORS.getOrDefault(teamId, NamedTextColor.WHITE);
    }

    /**
     * Retourne le code couleur hérité (ex: "§9") correspondant à l'équipe.
     *
     * @param teamId identifiant de l'équipe
     * @return code couleur legacy
     */
    public static String legacy(int teamId) {
        return LegacyComponentSerializer.legacySection().serialize(Component.text("", of(teamId)));
    }

    /**
     * Fournit un nom d'équipe coloré utilisant la couleur associée.
     *
     * @param teamId identifiant de l'équipe
     * @return nom d'équipe coloré
     */
    public static String coloredName(int teamId) {
        String base = switch (teamId) {
            case 1 -> "Équipe Bleue";
            case 2 -> "Équipe Rouge";
            default -> "Équipe " + teamId;
        };
        return legacy(teamId) + base;
    }
}
