package com.heneria.nexus.service.core.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable DTO describing the payload returned by the proxy after a teleport request.
 */
public record TeleportResultPayload(@JsonProperty("request_id") UUID requestId,
                                    @JsonProperty("status") String status,
                                    @JsonProperty("message") String message) {

    public TeleportResultPayload {
        status = Optional.ofNullable(status).orElse("");
        message = Optional.ofNullable(message).orElse("");
    }
}

