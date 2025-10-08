package fr.heneria.nexus.core.service;

import fr.heneria.nexus.NexusPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registre centralisé des services du plugin Nexus.
 * Gère l'enregistrement, la récupération et le cycle de vie de tous les services.
 *
 * <p>Pattern : Service Locator + Dependency Injection simple
 *
 * <p>Utilisation :
 * <pre>{@code
 * // Enregistrement
 * ServiceRegistry registry = new ServiceRegistry(plugin);
 * registry.register(ArenaService.class, new ArenaService(plugin));
 *
 * // Récupération
 * ArenaService arenaService = registry.get(ArenaService.class);
 *
 * // Cycle de vie
 * registry.initializeAll();
 * registry.startAll();
 * registry.shutdownAll();
 * }</pre>
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ServiceRegistry {
    
    private final NexusPlugin plugin;
    private final Logger logger;
    private final Map<Class<? extends Service>, Service> services;
    private final List<Class<? extends Service>> initializationOrder;
    private boolean initialized = false;
    private boolean started = false;
    
    /**
     * Constructeur du registre
     *
     * @param plugin Instance du plugin principal
     */
    public ServiceRegistry(NexusPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.services = new ConcurrentHashMap<>();
        this.initializationOrder = new ArrayList<>();
    }
    
    /**
     * Enregistre un service dans le registre.
     * Le service doit être instancié mais pas initialisé.
     *
     * @param <T> Type du service
     * @param serviceClass Classe du service
     * @param serviceInstance Instance du service
     * @throws IllegalStateException si le registre est déjà initialisé
     * @throws IllegalArgumentException si le service est déjà enregistré
     */
    public <T extends Service> void register(Class<T> serviceClass, T serviceInstance) {
        if (initialized) {
            throw new IllegalStateException("Cannot register services after initialization");
        }
        
        if (services.containsKey(serviceClass)) {
            throw new IllegalArgumentException("Service already registered: " + serviceClass.getSimpleName());
        }
        
        services.put(serviceClass, serviceInstance);
        initializationOrder.add(serviceClass);
        
        logger.info("✓ Service enregistré : " + serviceClass.getSimpleName());
    }
    
    /**
     * Récupère un service du registre.
     *
     * @param <T> Type du service
     * @param serviceClass Classe du service à récupérer
     * @return Instance du service
     * @throws IllegalStateException si le service n'est pas enregistré
     */
    @SuppressWarnings("unchecked")
    public <T extends Service> T get(Class<T> serviceClass) {
        Service service = services.get(serviceClass);
        if (service == null) {
            throw new IllegalStateException("Service not registered: " + serviceClass.getSimpleName());
        }
        return (T) service;
    }
    
    /**
     * Vérifie si un service est enregistré.
     *
     * @param serviceClass Classe du service
     * @return true si le service est enregistré, false sinon
     */
    public boolean isRegistered(Class<? extends Service> serviceClass) {
        return services.containsKey(serviceClass);
    }
    
    /**
     * Retourne tous les services enregistrés.
     *
     * @return Collection immuable des services
     */
    public Collection<Service> getAllServices() {
        return Collections.unmodifiableCollection(services.values());
    }
    
    /**
     * Initialise tous les services dans l'ordre d'enregistrement.
     * Appelle {@link Service#initialize()} sur chaque service.
     * Si un service échoue, les suivants ne sont pas initialisés.
     *
     * @throws ServiceException si l'initialisation d'un service échoue
     */
    public void initializeAll() throws ServiceException {
        if (initialized) {
            logger.warning("Services déjà initialisés, opération ignorée");
            return;
        }
        
        logger.info("========================================");
        logger.info("Initialisation des services...");
        logger.info("========================================");
        
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        
        for (Class<? extends Service> serviceClass : initializationOrder) {
            Service service = services.get(serviceClass);
            try {
                logger.info("→ Initialisation de " + service.getName() + "...");
                service.initialize();
                successCount++;
                logger.info("  ✓ " + service.getName() + " initialisé");
            } catch (ServiceException e) {
                logger.severe("  ✗ Échec de l'initialisation de " + service.getName());
                logger.severe("  Raison : " + e.getMessage());
                throw e;
            } catch (Exception e) {
                logger.severe("  ✗ Erreur inattendue lors de l'initialisation de " + service.getName());
                throw new ServiceException(
                    service.getName(),
                    ServiceException.Phase.INITIALIZATION,
                    "Erreur inattendue",
                    e
                );
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        initialized = true;
        
        logger.info("========================================");
        logger.info(String.format("✓ %d/%d services initialisés en %dms",
            successCount, services.size(), duration));
        logger.info("========================================");
    }
    
    /**
     * Démarre tous les services dans l'ordre d'enregistrement.
     * Appelle {@link Service#start()} sur chaque service.
     *
     * @throws ServiceException si le démarrage d'un service échoue
     */
    public void startAll() throws ServiceException {
        if (!initialized) {
            throw new IllegalStateException("Services must be initialized before starting");
        }
        
        if (started) {
            logger.warning("Services déjà démarrés, opération ignorée");
            return;
        }
        
        logger.info("Démarrage des services...");
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        
        for (Class<? extends Service> serviceClass : initializationOrder) {
            Service service = services.get(serviceClass);
            try {
                logger.info("→ Démarrage de " + service.getName() + "...");
                service.start();
                successCount++;
                logger.info("  ✓ " + service.getName() + " démarré");
            } catch (ServiceException e) {
                logger.severe("  ✗ Échec du démarrage de " + service.getName());
                logger.severe("  Raison : " + e.getMessage());
                throw e;
            } catch (Exception e) {
                logger.severe("  ✗ Erreur inattendue lors du démarrage de " + service.getName());
                throw new ServiceException(
                    service.getName(),
                    ServiceException.Phase.STARTUP,
                    "Erreur inattendue",
                    e
                );
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        started = true;
        
        logger.info(String.format("✓ %d/%d services démarrés en %dms",
            successCount, services.size(), duration));
    }
    
    /**
     * Arrête tous les services dans l'ORDRE INVERSE d'enregistrement.
     * Appelle {@link Service#shutdown()} sur chaque service.
     * Continue même si un service échoue (best-effort).
     */
    public void shutdownAll() {
        if (!initialized) {
            logger.warning("Services non initialisés, arrêt ignoré");
            return;
        }
        
        logger.info("========================================");
        logger.info("Arrêt des services...");
        logger.info("========================================");
        
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        List<ServiceException> errors = new ArrayList<>();
        
        List<Class<? extends Service>> shutdownOrder = new ArrayList<>(initializationOrder);
        Collections.reverse(shutdownOrder);
        
        for (Class<? extends Service> serviceClass : shutdownOrder) {
            Service service = services.get(serviceClass);
            try {
                logger.info("→ Arrêt de " + service.getName() + "...");
                service.shutdown();
                successCount++;
                logger.info("  ✓ " + service.getName() + " arrêté");
            } catch (ServiceException e) {
                logger.warning("  ⚠ Erreur lors de l'arrêt de " + service.getName());
                logger.warning("  Raison : " + e.getMessage());
                errors.add(e);
            } catch (Exception e) {
                logger.warning("  ⚠ Erreur inattendue lors de l'arrêt de " + service.getName());
                errors.add(new ServiceException(
                    service.getName(),
                    ServiceException.Phase.SHUTDOWN,
                    "Erreur inattendue",
                    e
                ));
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        logger.info("========================================");
        if (errors.isEmpty()) {
            logger.info(String.format("✓ Tous les services arrêtés proprement en %dms", duration));
        } else {
            logger.warning(String.format("⚠ %d/%d services arrêtés avec erreurs en %dms",
                successCount, services.size(), duration));
            logger.warning("Erreurs : " + errors.size());
        }
        logger.info("========================================");
        
        initialized = false;
        started = false;
    }
    
    /**
     * Retourne le statut global du registre.
     *
     * @return true si tous les services sont initialisés et démarrés
     */
    public boolean isHealthy() {
        return initialized && started && services.values().stream().allMatch(Service::isRunning);
    }
    
    /**
     * Génère un rapport de statut des services.
     *
     * @return Rapport détaillé formaté
     */
    public String generateStatusReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== STATUS REPORT - SERVICES ===\n");
        report.append("Initialisé : ").append(initialized).append("\n");
        report.append("Démarré : ").append(started).append("\n");
        report.append("Services enregistrés : ").append(services.size()).append("\n\n");
        
        for (Class<? extends Service> serviceClass : initializationOrder) {
            Service service = services.get(serviceClass);
            report.append("- ")
                  .append(service.getName())
                  .append(" : ")
                  .append(service.isRunning() ? "✓ ACTIF" : "✗ INACTIF")
                  .append("\n");
        }
        
        report.append("================================");
        return report.toString();
    }
}
