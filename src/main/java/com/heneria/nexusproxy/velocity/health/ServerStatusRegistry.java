package com.heneria.nexusproxy.velocity.health;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry storing health information for every Nexus backend.
 */
public final class ServerStatusRegistry {

    private final ConcurrentHashMap<String, ServerStatusSnapshot> statuses = new ConcurrentHashMap<>();

    public void update(ServerStatusSnapshot snapshot) {
        statuses.put(snapshot.serverId(), snapshot);
    }

    public Optional<ServerStatusSnapshot> get(String serverId) {
        if (serverId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(statuses.get(serverId));
    }

    public Collection<ServerStatusSnapshot> snapshot() {
        List<ServerStatusSnapshot> copy = new ArrayList<>(statuses.values());
        copy.sort(Comparator.comparing(ServerStatusSnapshot::serverId));
        return List.copyOf(copy);
    }

    public boolean isAcceptingPlayers(String serverId) {
        return get(serverId).map(ServerStatusSnapshot::canAcceptPlayers).orElse(true);
    }

    public Optional<String> firstAvailable(Collection<String> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Optional.empty();
        }
        for (String candidate : candidateIds) {
            if (isAcceptingPlayers(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public void markExpired(Duration timeout, Instant now) {
        if (timeout.isZero() || timeout.isNegative()) {
            return;
        }
        for (Map.Entry<String, ServerStatusSnapshot> entry : statuses.entrySet()) {
            ServerStatusSnapshot snapshot = entry.getValue();
            if (snapshot == null) {
                continue;
            }
            Duration age = Duration.between(snapshot.lastUpdate(), now);
            if (age.compareTo(timeout) > 0) {
                statuses.compute(entry.getKey(), (key, existing) -> {
                    if (existing == null) {
                        return null;
                    }
                    if (Duration.between(existing.lastUpdate(), now).compareTo(timeout) > 0) {
                        return existing.markOffline(now);
                    }
                    return existing;
                });
            }
        }
    }
}
