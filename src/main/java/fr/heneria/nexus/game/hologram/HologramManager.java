package fr.heneria.nexus.game.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gestion simple des hologrammes de capture via DecentHolograms.
 */
public class HologramManager {

    private Hologram captureHologram;

    /**
     * Crée l'hologramme de capture à l'emplacement donné.
     *
     * @param location position de la cellule d'énergie
     */
    public void createCaptureHologram(Location location) {
        removeCaptureHologram();
        List<String> lines = new ArrayList<>();
        lines.add("§e§lCELLULE D'ÉNERGIE");
        captureHologram = DHAPI.createHologram("capture-" + System.currentTimeMillis(), location, lines);
    }

    /**
     * Met à jour l'hologramme avec les progressions de chaque équipe.
     *
     * @param progresses        progression de chaque équipe
     * @param capturingTeamId   identifiant de l'équipe capturant actuellement (-1 si aucune)
     * @param contested         true si la cellule est contestée
     */
    public void updateCaptureHologram(Map<Integer, Double> progresses, int capturingTeamId, boolean contested) {
        if (captureHologram == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("§e§lCELLULE D'ÉNERGIE");
        List<Integer> teamIds = new ArrayList<>(progresses.keySet());
        Collections.sort(teamIds);
        for (int teamId : teamIds) {
            double progress = progresses.getOrDefault(teamId, 0D);
            String name = switch (teamId) {
                case 1 -> "§9Équipe Bleue";
                case 2 -> "§cÉquipe Rouge";
                default -> "Équipe " + teamId;
            };
            lines.add(name + ": §f" + (int) progress + " / 60s");
        }
        if (contested) {
            lines.add("§eContesté !");
        } else if (capturingTeamId != -1) {
            lines.add("§aCapture en cours par l'équipe " + capturingTeamId);
        }
        DHAPI.setHologramLines(captureHologram, lines);
    }

    /**
     * Supprime l'hologramme de capture existant.
     */
    public void removeCaptureHologram() {
        if (captureHologram != null) {
            captureHologram.delete();
            captureHologram = null;
        }
    }
}

