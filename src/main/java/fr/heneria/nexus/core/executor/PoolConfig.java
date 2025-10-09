package fr.heneria.nexus.core.executor;

/**
 * Configuration d'un pool d'executors.
 * Utilise un Record Java pour l'immutabilité.
 *
 * @param name Nom du pool (pour logging et monitoring)
 * @param coreSize Nombre de threads minimum
 * @param maxSize Nombre de threads maximum
 * @param queueSize Taille de la queue (0 = illimité)
 * @param keepAliveSeconds Durée avant destruction des threads idle
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public record PoolConfig(
    String name,
    int coreSize,
    int maxSize,
    int queueSize,
    int keepAliveSeconds
) {
    /**
     * Validation à la construction
     */
    public PoolConfig {
        if (coreSize < 1) {
            throw new IllegalArgumentException("coreSize doit être >= 1");
        }
        if (maxSize < coreSize) {
            throw new IllegalArgumentException("maxSize doit être >= coreSize");
        }
        if (keepAliveSeconds < 0) {
            throw new IllegalArgumentException("keepAliveSeconds doit être >= 0");
        }
    }

    /**
     * Configuration par défaut pour I/O (DB, fichiers).
     * Pool petit mais extensible.
     *
     * @return Config I/O
     */
    public static PoolConfig ioDefault() {
        return new PoolConfig("Nexus-IO", 2, 8, 100, 60);
    }

    /**
     * Configuration par défaut pour compute (calculs lourds).
     * Pool dimensionné selon les CPUs.
     *
     * @return Config Compute
     */
    public static PoolConfig computeDefault() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int core = Math.max(2, cpus / 2);
        int max = Math.max(4, cpus);
        return new PoolConfig("Nexus-Compute", core, max, 50, 30);
    }
}
