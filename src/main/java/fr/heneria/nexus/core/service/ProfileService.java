package fr.heneria.nexus.core.service;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.core.executor.Async;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service de gestion des profils joueurs.
 * Gère le chargement, la sauvegarde et le cache des données joueurs.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class ProfileService extends AbstractService {
    
    public ProfileService(NexusPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "ProfileService";
    }
    
    @Override
    protected void onInitialize() throws Exception {
        logger.info("  [ProfileService] Initialisation...");
        // TODO T-024: Setup du cache LRU
    }
    
    @Override
    protected void onStart() throws Exception {
        logger.info("  [ProfileService] Démarrage...");
        // TODO T-024: Enregistrement des listeners
    }
    
    @Override
    protected void onShutdown() throws Exception {
        logger.info("  [ProfileService] Arrêt...");
        // TODO T-024: Flush tous les profils en cache
    }

    /**
     * EXEMPLE : Charge un profil de manière asynchrone depuis la DB.
     *
     * @param uuid UUID du joueur
     * @return CompletableFuture du profil (ou null si pas trouvé)
     */
    public CompletableFuture<Object> loadProfileAsync(UUID uuid) {
        var ioPool = plugin.getExecutorManager().getIoPool();

        return Async.supply(() -> {
            logger.info("[ASYNC] Chargement du profil " + uuid + " depuis la DB...");

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("[ASYNC] Profil " + uuid + " chargé");
            return new Object();
        }, ioPool);
    }

    /**
     * EXEMPLE : Charge un profil async puis update l'UI sur le main thread.
     *
     * @param uuid UUID du joueur
     */
    public void loadProfileAndUpdateUI(UUID uuid) {
        var ioPool = plugin.getExecutorManager().getIoPool();

        Async.supplyThenSync(
            () -> {
                logger.info("[ASYNC] Chargement du profil...");
                return new Object();
            },
            ioPool,
            profile -> {
                logger.info("[SYNC] Mise à jour de l'UI pour " + uuid);
            }
        );
    }
}
