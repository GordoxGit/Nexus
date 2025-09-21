package com.heneria.nexus.hologram;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;

/**
 * API publique pour la gestion des hologrammes Nexus.
 */
public interface HoloService extends LifecycleAware {

    /**
     * Recharge toutes les définitions depuis holograms.yml.
     */
    void loadFromConfig();

    /**
     * Crée un nouvel hologramme dynamique.
     */
    Hologram createHologram(String id, Location location, List<String> lines);

    /**
     * Retourne un hologramme par son identifiant unique.
     */
    Optional<Hologram> getHologram(String id);

    /**
     * Supprime un hologramme et libère ses entités.
     */
    void removeHologram(String id);

    /**
     * Retourne la collection des hologrammes actifs.
     */
    Collection<Hologram> holograms();

    /**
     * Applique les paramètres runtime provenant de config.yml.
     */
    void applySettings(CoreConfig.HologramSettings settings);

    /**
     * Retourne les statistiques courantes utilisées par /nexus dump.
     */
    Diagnostics diagnostics();

    record Diagnostics(int activeHolograms, int pooledTextDisplays, int pooledInteractions) {
    }
}