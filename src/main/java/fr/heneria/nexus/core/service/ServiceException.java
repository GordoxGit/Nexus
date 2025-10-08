package fr.heneria.nexus.core.service;

/**
 * Exception levée lors d'erreurs dans le cycle de vie des services.
 * Encapsule les erreurs d'initialisation, démarrage ou arrêt.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ServiceException extends Exception {
    
    private final String serviceName;
    private final Phase phase;
    
    /**
     * Phase du cycle de vie où l'erreur s'est produite
     */
    public enum Phase {
        INITIALIZATION("Initialisation"),
        STARTUP("Démarrage"),
        SHUTDOWN("Arrêt"),
        RUNTIME("Exécution");
        
        private final String displayName;
        
        Phase(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Constructeur principal
     *
     * @param serviceName Nom du service en erreur
     * @param phase Phase du cycle de vie
     * @param message Message d'erreur descriptif
     */
    public ServiceException(String serviceName, Phase phase, String message) {
        super(String.format("[%s] %s - %s", serviceName, phase.getDisplayName(), message));
        this.serviceName = serviceName;
        this.phase = phase;
    }
    
    /**
     * Constructeur avec cause
     *
     * @param serviceName Nom du service en erreur
     * @param phase Phase du cycle de vie
     * @param message Message d'erreur descriptif
     * @param cause Exception originale
     */
    public ServiceException(String serviceName, Phase phase, String message, Throwable cause) {
        super(String.format("[%s] %s - %s", serviceName, phase.getDisplayName(), message), cause);
        this.serviceName = serviceName;
        this.phase = phase;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public Phase getPhase() {
        return phase;
    }
}
