package fr.heneria.nexus.core.service;

/**
 * Interface de base pour tous les services du plugin Nexus.
 * Définit le cycle de vie standardisé : initialisation, démarrage, arrêt.
 *
 * <p>Ordre d'exécution :
 * <ol>
 *   <li>{@link #initialize()} - Préparation (chargement config, connexions)</li>
 *   <li>{@link #start()} - Démarrage effectif (schedulers, listeners)</li>
 *   <li>{@link #shutdown()} - Arrêt propre (sauvegarde, cleanup)</li>
 * </ol>
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public interface Service {
    
    /**
     * Initialise le service.
     * Appelé durant {@link org.bukkit.plugin.java.JavaPlugin#onEnable()}.
     * Charge la configuration, établit les connexions, prépare les ressources.
     *
     * @throws ServiceException si l'initialisation échoue
     */
    void initialize() throws ServiceException;
    
    /**
     * Démarre le service après initialisation complète.
     * Démarre les schedulers, enregistre les listeners, lance les tâches.
     *
     * @throws ServiceException si le démarrage échoue
     */
    void start() throws ServiceException;
    
    /**
     * Arrête le service proprement.
     * Appelé durant {@link org.bukkit.plugin.java.JavaPlugin#onDisable()}.
     * Sauvegarde les données, ferme les connexions, annule les tâches.
     *
     * @throws ServiceException si l'arrêt rencontre une erreur non-critique
     */
    void shutdown() throws ServiceException;
    
    /**
     * Retourne le nom du service pour les logs et l'identification.
     *
     * @return Nom unique du service (ex: "ArenaService", "DatabaseService")
     */
    String getName();
    
    /**
     * Indique si le service est actuellement actif et opérationnel.
     *
     * @return true si le service est démarré et fonctionnel, false sinon
     */
    boolean isRunning();
}
