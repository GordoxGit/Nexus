package com.heneria.nexus.api;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for orchestrating cross-server teleports through the
 * Velocity plugin messaging bridge.
 */
public interface TeleportService extends LifecycleAware {

    /** Plugin messaging channel shared with the proxy. */
    String CHANNEL = "nexus:main";

    /**
     * Requests the proxy to connect the provided player to the default Nexus
     * arena server configured for this instance.
     *
     * @param playerId unique identifier of the player to teleport
     * @return asynchronous result of the teleport request
     */
    default CompletableFuture<TeleportResult> connectToArena(UUID playerId) {
        return connectToArena(playerId, null);
    }

    /**
     * Requests the proxy to connect the provided player to the supplied Nexus
     * arena server identifier.
     *
     * @param playerId unique identifier of the player to teleport
     * @param serverId optional target server identifier (falls back to the
     *                 configured default when {@code null})
     * @return asynchronous result of the teleport request
     */
    CompletableFuture<TeleportResult> connectToArena(UUID playerId, String serverId);

    /**
     * Requests the proxy to send the provided player back to the hub group.
     *
     * @param playerId unique identifier of the player to teleport
     * @return asynchronous result of the teleport request
     */
    CompletableFuture<TeleportResult> returnToHub(UUID playerId);

    /**
     * Updates the runtime configuration bound to the teleport service.
     *
     * @param settings latest queue configuration bundle
     */
    void applySettings(CoreConfig.QueueSettings settings);

    /**
     * Immutable representation of the status returned for a teleport request.
     */
    record TeleportResult(UUID requestId,
                          UUID playerId,
                          TeleportAction action,
                          TeleportStatus status,
                          String message) {

        public TeleportResult {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(status, "status");
            message = Optional.ofNullable(message).orElse("");
        }

        /**
         * @return {@code true} when the teleport request completed successfully.
         */
        public boolean success() {
            return status == TeleportStatus.SUCCESS;
        }
    }

    /** Describes the action associated with a teleport request. */
    enum TeleportAction {
        CONNECT,
        RETURN_TO_HUB
    }

    /** Enumerates the known outcomes of a teleport request. */
    enum TeleportStatus {
        SUCCESS,
        FAILED,
        RETRYABLE,
        TIMEOUT
    }
}
