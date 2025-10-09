package fr.heneria.nexus.arena.phase;

/**
 * Phases du cycle de vie d'une arène.
 * Chaque phase a des besoins de traitement et fréquences différents.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public enum ArenaPhase {

    /**
     * LOBBY : Attente des joueurs, pré-warm.
     * Fréquence basse (2 Hz) - Pas de logique critique.
     */
    LOBBY(2, "Lobby", false),

    /**
     * STARTING : Countdown avant début de partie.
     * Fréquence moyenne (5 Hz) - UI updates, vérifications.
     */
    STARTING(5, "Démarrage", false),

    /**
     * PLAYING : Jeu actif, gameplay complet.
     * Fréquence haute (10 Hz) - Critique pour la réactivité.
     */
    PLAYING(10, "En jeu", true),

    /**
     * SCORED : Fin de manche, gel des interactions.
     * Fréquence basse (2 Hz) - Affichage résultats, stats.
     */
    SCORED(2, "Manche terminée", false),

    /**
     * RESET : Restauration du monde, cleanup.
     * Fréquence variable - Dépend de la méthode de reset.
     */
    RESET(5, "Reset", false),

    /**
     * END : Fin de partie complète, téléportation hub.
     * Fréquence basse (2 Hz) - Nettoyage final.
     */
    END(2, "Terminé", false);

    private final int tickRate;
    private final String displayName;
    private final boolean critical;

    ArenaPhase(int tickRate, String displayName, boolean critical) {
        this.tickRate = tickRate;
        this.displayName = displayName;
        this.critical = critical;
    }

    /**
     * Retourne la fréquence de tick en Hz.
     *
     * @return Ticks par seconde
     */
    public int getTickRate() {
        return tickRate;
    }

    /**
     * Retourne l'intervalle entre ticks en server ticks (20 ticks = 1 seconde).
     *
     * @return Interval en ticks Minecraft
     */
    public long getTickInterval() {
        return 20L / tickRate;
    }

    /**
     * Retourne le nom d'affichage de la phase.
     *
     * @return Nom français
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Indique si cette phase est critique pour la performance.
     * Les phases critiques doivent être optimisées en priorité.
     *
     * @return true si critique
     */
    public boolean isCritical() {
        return critical;
    }

    /**
     * Retourne le budget MSPT recommandé pour cette phase.
     * Budget plus élevé pour les phases critiques.
     *
     * @return Budget en millisecondes
     */
    public int getRecommendedMsptBudget() {
        return critical ? 8 : 3;
    }
}
