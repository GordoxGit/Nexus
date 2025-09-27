package com.heneria.nexus.db.repository;

import com.heneria.nexus.audit.AuditActionType;
import com.heneria.nexus.audit.AuditLogRecord;
import com.heneria.nexus.db.ResilientDbExecutor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MariaDB backed implementation of {@link AuditLogRepository}.
 */
public final class AuditLogRepositoryImpl implements AuditLogRepository {

    private static final String INSERT_SQL =
            "INSERT INTO nexus_audit_logs (timestamp, actor_uuid, actor_name, action_type, target_uuid, target_name, details) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String BASE_SELECT_SQL =
            "SELECT log_id, timestamp, actor_uuid, actor_name, action_type, target_uuid, target_name, details " +
                    "FROM nexus_audit_logs";

    private final ResilientDbExecutor dbExecutor;

    public AuditLogRepositoryImpl(ResilientDbExecutor dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    @Override
    public CompletableFuture<Void> saveAll(Collection<AuditLogRecord> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return dbExecutor.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                for (AuditLogRecord entry : entries) {
                    statement.setTimestamp(1, Timestamp.from(entry.timestamp()));
                    setUuid(statement, 2, entry.actorUuid());
                    setString(statement, 3, entry.actorName());
                    statement.setString(4, entry.actionType().databaseValue());
                    setUuid(statement, 5, entry.targetUuid());
                    setString(statement, 6, entry.targetName());
                    setString(statement, 7, entry.details());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<AuditLogRecord>> findRecent(Optional<UUID> subjectUuid,
                                                               Optional<String> subjectName,
                                                               int limit,
                                                               int offset) {
        Objects.requireNonNull(subjectUuid, "subjectUuid");
        Objects.requireNonNull(subjectName, "subjectName");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        return dbExecutor.execute(connection -> {
            StringBuilder sql = new StringBuilder(BASE_SELECT_SQL);
            List<Object> parameters = new ArrayList<>();
            boolean whereAdded = false;
            if (subjectUuid.isPresent()) {
                sql.append(whereAdded ? " AND" : " WHERE");
                sql.append(" (actor_uuid = ? OR target_uuid = ?)");
                String uuid = subjectUuid.get().toString();
                parameters.add(uuid);
                parameters.add(uuid);
                whereAdded = true;
            }
            if (subjectName.isPresent()) {
                sql.append(whereAdded ? " AND" : " WHERE");
                sql.append(" (actor_name = ? OR target_name = ?)");
                String name = subjectName.get();
                parameters.add(name);
                parameters.add(name);
                whereAdded = true;
            }
            sql.append(" ORDER BY log_id DESC LIMIT ? OFFSET ?");
            parameters.add(limit);
            parameters.add(offset);
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < parameters.size(); i++) {
                    Object parameter = parameters.get(i);
                    if (parameter instanceof String string) {
                        statement.setString(i + 1, string);
                    } else if (parameter instanceof Integer integer) {
                        statement.setInt(i + 1, integer);
                    } else {
                        statement.setObject(i + 1, parameter);
                    }
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<AuditLogRecord> records = new ArrayList<>();
                    while (resultSet.next()) {
                        records.add(mapRow(resultSet));
                    }
                    return records;
                }
            }
        });
    }

    private AuditLogRecord mapRow(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("log_id");
        Timestamp timestamp = resultSet.getTimestamp("timestamp");
        Instant instant = timestamp != null ? timestamp.toInstant() : Instant.EPOCH;
        String actorUuid = resultSet.getString("actor_uuid");
        String targetUuid = resultSet.getString("target_uuid");
        return new AuditLogRecord(
                id,
                instant,
                actorUuid != null ? UUID.fromString(actorUuid) : null,
                resultSet.getString("actor_name"),
                AuditActionType.fromDatabaseValue(resultSet.getString("action_type")),
                targetUuid != null ? UUID.fromString(targetUuid) : null,
                resultSet.getString("target_name"),
                resultSet.getString("details"));
    }

    private void setUuid(PreparedStatement statement, int index, UUID uuid) throws SQLException {
        if (uuid == null) {
            statement.setNull(index, Types.CHAR);
            return;
        }
        statement.setString(index, uuid.toString());
    }

    private void setString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }
}
