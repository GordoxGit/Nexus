package fr.heneria.nexus.core.executor;

import fr.heneria.nexus.NexusPlugin;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitaires pour simplifier les opérations asynchrones.
 * Fournit des wrappers pour CompletableFuture avec gestion d'erreurs intégrée.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public final class Async {

    private static final Logger LOGGER = NexusPlugin.getInstance().getLogger();

    private Async() {
        throw new UnsupportedOperationException("Classe utilitaire");
    }

    /**
     * Exécute une tâche asynchrone et retourne immédiatement un CompletableFuture.
     *
     * @param <T> Type du résultat
     * @param supplier Fournisseur du résultat (exécuté async)
     * @param executor Executor à utiliser
     * @return CompletableFuture du résultat
     */
    public static <T> CompletableFuture<T> supply(Supplier<T> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(supplier, executor)
            .exceptionally(throwable -> {
                LOGGER.log(Level.SEVERE, "Erreur dans une tâche asynchrone", throwable);
                return null;
            });
    }

    /**
     * Exécute une tâche asynchrone sans retour.
     *
     * @param runnable Tâche à exécuter
     * @param executor Executor à utiliser
     * @return CompletableFuture<Void>
     */
    public static CompletableFuture<Void> run(Runnable runnable, Executor executor) {
        return CompletableFuture.runAsync(runnable, executor)
            .exceptionally(throwable -> {
                LOGGER.log(Level.SEVERE, "Erreur dans une tâche asynchrone", throwable);
                return null;
            });
    }

    /**
     * Exécute une tâche asynchrone, puis exécute le callback sur le main thread.
     * Pattern courant : fetch data async → update UI sync.
     *
     * @param <T> Type du résultat
     * @param supplier Fournisseur async du résultat
     * @param executor Executor pour la tâche async
     * @param callback Callback exécuté sur le main thread avec le résultat
     */
    public static <T> void supplyThenSync(
        Supplier<T> supplier,
        Executor executor,
        Consumer<T> callback
    ) {
        supply(supplier, executor).thenAccept(result -> {
            if (result != null) {
                runSync(() -> callback.accept(result));
            }
        });
    }

    /**
     * Exécute une tâche sur le main thread de Bukkit.
     *
     * @param runnable Tâche à exécuter
     */
    public static void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(NexusPlugin.getInstance(), runnable);
        }
    }

    /**
     * Exécute une tâche sur le main thread avec un délai.
     *
     * @param runnable Tâche à exécuter
     * @param delayTicks Délai en ticks (20 ticks = 1 seconde)
     */
    public static void runSyncLater(Runnable runnable, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(NexusPlugin.getInstance(), runnable, delayTicks);
    }

    /**
     * Exécute une tâche async, puis un callback sync, avec gestion d'erreur.
     *
     * @param <T> Type du résultat
     * @param supplier Fournisseur async
     * @param executor Executor pour la tâche async
     * @param onSuccess Callback de succès (main thread)
     * @param onError Callback d'erreur (main thread)
     */
    public static <T> void supplyThenSyncWithError(
        Supplier<T> supplier,
        Executor executor,
        Consumer<T> onSuccess,
        Consumer<Throwable> onError
    ) {
        CompletableFuture.supplyAsync(supplier, executor)
            .whenComplete((result, throwable) -> {
                runSync(() -> {
                    if (throwable != null) {
                        onError.accept(throwable);
                    } else if (result != null) {
                        onSuccess.accept(result);
                    }
                });
            });
    }

    /**
     * Attend qu'un CompletableFuture soit complété avec timeout.
     * NE DOIT PAS être appelé sur le main thread !
     *
     * @param <T> Type du résultat
     * @param future Future à attendre
     * @param timeoutSeconds Timeout en secondes
     * @return Résultat ou null si timeout/erreur
     */
    public static <T> T awaitOrNull(CompletableFuture<T> future, int timeoutSeconds) {
        if (Bukkit.isPrimaryThread()) {
            LOGGER.severe("ERREUR: awaitOrNull appelé sur le main thread !");
            Thread.dumpStack();
            return null;
        }

        try {
            return future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Timeout ou erreur lors de l'attente d'un future", e);
            return null;
        }
    }

    /**
     * Crée un CompletableFuture déjà complété avec une valeur.
     *
     * @param <T> Type de la valeur
     * @param value Valeur
     * @return CompletableFuture complété
     */
    public static <T> CompletableFuture<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    /**
     * Crée un CompletableFuture déjà en erreur.
     *
     * @param <T> Type du résultat
     * @param throwable Exception
     * @return CompletableFuture en erreur
     */
    public static <T> CompletableFuture<T> failed(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}
