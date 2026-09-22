package com.loading.process.repository;

import com.loading.process.model.EventLogRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class EventLogRepository {

    private final DatabaseConnectionProvider connectionProvider;

    public EventLogRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        initializeDatabase();
    }

    public EventLogRecord save(EventLogRecord event) {
        String sql = """
                INSERT INTO EventLog (id, objectId, userId, timestamp, eventType, message)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.id().toString());
            statement.setString(2, event.objectId().toString());
            setUuid(statement, 3, event.userId());
            statement.setTimestamp(4, Timestamp.valueOf(event.timestamp()));
            statement.setString(5, event.eventType());
            statement.setString(6, event.message());
            statement.executeUpdate();
            return event;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save event log to SQLite", e);
        }
    }

    public EventLogRecord update(EventLogRecord event) {
        String sql = """
                UPDATE EventLog
                SET objectId = ?, userId = ?, timestamp = ?, eventType = ?, message = ?
                WHERE id = ?
                """;
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.objectId().toString());
            setUuid(statement, 2, event.userId());
            statement.setTimestamp(3, Timestamp.valueOf(event.timestamp()));
            statement.setString(4, event.eventType());
            statement.setString(5, event.message());
            statement.setString(6, event.id().toString());
            statement.executeUpdate();
            return event;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update event log in SQLite", e);
        }
    }

    public EventLogRecord findById(UUID id) {
        String sql = """
                SELECT id, objectId, userId, timestamp, eventType, message
                FROM EventLog
                WHERE id = ?
                """;
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read event log from SQLite", e);
        }
    }

    public List<EventLogRecord> findByObjectId(UUID objectId) {
        String sql = """
                SELECT id, objectId, userId, timestamp, eventType, message
                FROM EventLog
                WHERE objectId = ?
                ORDER BY timestamp DESC
                """;
        List<EventLogRecord> events = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, objectId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(mapRow(resultSet));
                }
            }
            return events;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read object event logs from SQLite", e);
        }
    }

    public List<EventLogRecord> findAll() {
        String sql = """
                SELECT id, objectId, userId, timestamp, eventType, message
                FROM EventLog
                ORDER BY timestamp DESC
                """;
        List<EventLogRecord> events = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                events.add(mapRow(resultSet));
            }
            return events;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read event logs from SQLite", e);
        }
    }

    public void delete(UUID id) {
        executeDelete("DELETE FROM EventLog WHERE id = ?", id);
    }

    private void initializeDatabase() {
        try (Connection connection = connectionProvider.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS EventLog (
                        id TEXT PRIMARY KEY,
                        objectId TEXT NOT NULL,
                        userId TEXT,
                        timestamp TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        message TEXT NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize EventLog table", e);
        }
    }

    private void executeDelete(String sql, UUID id) {
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete event log from SQLite", e);
        }
    }

    private EventLogRecord mapRow(ResultSet resultSet) throws SQLException {
        String userId = resultSet.getString("userId");
        return new EventLogRecord(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("objectId")),
                userId == null ? null : UUID.fromString(userId),
                resultSet.getTimestamp("timestamp").toLocalDateTime(),
                resultSet.getString("eventType"),
                resultSet.getString("message"));
    }

    private void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value.toString());
        }
    }
}
