package com.heneria.nexus.analytics;

import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.util.NexusLogger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Repository responsible for persisting analytics events into the database.
 */
public final class AnalyticsRepository {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS nexus_analytics_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                event_type VARCHAR(64) NOT NULL,
                event_timestamp TIMESTAMP(6) NOT NULL,
                player_id CHAR(36) NULL,
                event_data JSON NOT NULL,
                INDEX idx_event_type (event_type),
                INDEX idx_event_timestamp (event_timestamp)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String INSERT_SQL = "INSERT INTO nexus_analytics_log "
            + "(event_type, event_timestamp, player_id, event_data) VALUES (?, ?, ?, ?)";

    private final DbProvider dbProvider;
    private final ExecutorManager executorManager;
    private final NexusLogger logger;
    private final AtomicBoolean tableReady = new AtomicBoolean();
    private final Object schemaLock = new Object();

    public AnalyticsRepository(DbProvider dbProvider,
                               ExecutorManager executorManager,
                               NexusLogger logger) {
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletableFuture<Integer> saveBatch(List<AnalyticsEvent> events) {
        Objects.requireNonNull(events, "events");
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return dbProvider.execute("AnalyticsRepository::saveBatch",
                connection -> persistBatch(connection, events), executorManager.io());
    }

    private int persistBatch(Connection connection, List<AnalyticsEvent> events) throws SQLException {
        ensureSchema(connection);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            for (AnalyticsEvent event : events) {
                statement.setString(1, event.eventType());
                statement.setTimestamp(2, Timestamp.from(event.timestamp()));
                Optional<UUID> playerId = event.playerId();
                if (playerId.isPresent()) {
                    statement.setString(3, playerId.get().toString());
                } else {
                    statement.setNull(3, Types.CHAR);
                }
                statement.setString(4, PayloadSerializer.toJson(event.data()));
                statement.addBatch();
            }
            int[] updates = statement.executeBatch();
            int total = 0;
            for (int update : updates) {
                if (update > 0) {
                    total += update;
                } else if (update == Statement.SUCCESS_NO_INFO) {
                    total += 1;
                }
            }
            return total;
        }
    }

    private void ensureSchema(Connection connection) throws SQLException {
        if (tableReady.get()) {
            return;
        }
        synchronized (schemaLock) {
            if (tableReady.get()) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_TABLE_SQL);
                tableReady.set(true);
                logger.debug("Table nexus_analytics_log prête");
            }
        }
    }

    private static final class PayloadSerializer {

        private PayloadSerializer() {
        }

        private static String toJson(Map<String, Object> payload) {
            Objects.requireNonNull(payload, "payload");
            StringBuilder builder = new StringBuilder();
            appendMap(builder, payload);
            return builder.toString();
        }

        @SuppressWarnings("unchecked")
        private static void appendValue(StringBuilder builder, Object value) {
            if (value == null) {
                builder.append("null");
                return;
            }
            if (value instanceof Map<?, ?> map) {
                appendMap(builder, map.entrySet().stream()
                        .collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()), Map.Entry::getValue)));
                return;
            }
            if (value instanceof Collection<?> collection) {
                appendCollection(builder, collection);
                return;
            }
            if (value.getClass().isArray()) {
                appendCollection(builder, arrayToCollection(value));
                return;
            }
            if (value instanceof CharSequence sequence) {
                appendString(builder, sequence.toString());
                return;
            }
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value.toString());
                return;
            }
            if (value instanceof Instant instant) {
                appendString(builder, instant.toString());
                return;
            }
            if (value instanceof Duration duration) {
                builder.append(duration.toMillis());
                return;
            }
            if (value instanceof UUID uuid) {
                appendString(builder, uuid.toString());
                return;
            }
            if (value instanceof Enum<?> enumeration) {
                appendString(builder, enumeration.name());
                return;
            }
            if (value instanceof Optional<?> optional) {
                appendValue(builder, optional.orElse(null));
                return;
            }
            appendString(builder, value.toString());
        }

        private static void appendCollection(StringBuilder builder, Collection<?> values) {
            builder.append('[');
            Iterator<?> iterator = values.iterator();
            while (iterator.hasNext()) {
                appendValue(builder, iterator.next());
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append(']');
        }

        private static void appendMap(StringBuilder builder, Map<String, Object> values) {
            builder.append('{');
            Iterator<Map.Entry<String, Object>> iterator = values.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                appendString(builder, entry.getKey());
                builder.append(':');
                appendValue(builder, entry.getValue());
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append('}');
        }

        private static void appendString(StringBuilder builder, String value) {
            builder.append('"');
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                switch (character) {
                    case '"', '\\' -> builder.append('\\').append(character);
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            builder.append(String.format("\\u%04x", (int) character));
                        } else {
                            builder.append(character);
                        }
                    }
                }
            }
            builder.append('"');
        }

        private static Collection<?> arrayToCollection(Object array) {
            if (array instanceof Object[] objects) {
                return List.of(objects);
            }
            int length = java.lang.reflect.Array.getLength(array);
            java.util.ArrayList<Object> copy = new java.util.ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(java.lang.reflect.Array.get(array, index));
            }
            return copy;
        }
    }
}
