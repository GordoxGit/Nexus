package fr.heneria.nexus.arena.phase;

/**
 * Interface pour les systèmes qui nécessitent un tick régulier.
 * Tous les systèmes de gameplay (Nexus, Cellules, Score, etc.) l'implémentent.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public interface Tickable {

    /**
     * Appelé à chaque tick selon la fréquence de la phase.
     * NE DOIT PAS bloquer ou prendre trop de temps !
     *
     * @param phase     Phase actuelle de l'arène
     * @param tickCount Compteur de ticks depuis le début de la phase
     */
    void tick(ArenaPhase phase, long tickCount);

    /**
     * Indique si ce système doit être tick dans la phase donnée.
     * Permet de désactiver des systèmes inutiles selon la phase.
     *
     * @param phase Phase à vérifier
     * @return true si le tick est nécessaire
     */
    default boolean shouldTick(ArenaPhase phase) {
        return true;
    }

    /**
     * Retourne le nom du système pour le monitoring.
     *
     * @return Nom du système (ex: "NexusSystem", "CelluleSystem")
     */
    String getSystemName();
}
