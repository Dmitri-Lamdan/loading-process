package com.loading.process.repository;

import com.loading.process.model.ValueHistoryRecord;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

@Repository
public class ValueHistoryRepository {

    private final DatabaseConnectionProvider connectionProvider;

    public ValueHistoryRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ValueHistory (
                        id TEXT PRIMARY KEY,
                        objectId TEXT NOT NULL,
                        timestamp TEXT NOT NULL,
                        value TEXT NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize ValueHistory table", e);
        }
    }

    public ValueHistoryRecord save(ValueHistoryRecord rec) {
        String sql = "INSERT INTO ValueHistory (id, objectId, timestamp, value) VALUES (?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rec.id().toString());
            statement.setString(2, rec.objectId().toString());
            statement.setString(3, rec.timestamp().toString());
            statement.setString(4, rec.value() == null ? null : rec.value().toPlainString());
            statement.executeUpdate();
            return rec;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save value history to SQLite", e);
        }
    }

    public ValueHistoryRecord update(ValueHistoryRecord rec) {
        String sql = "UPDATE ValueHistory SET objectId = ?, timestamp = ?, value = ? WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rec.objectId().toString());
            statement.setString(2, rec.timestamp().toString());
            statement.setString(3, rec.value() == null ? null : rec.value().toPlainString());
            statement.setString(4, rec.id().toString());
            statement.executeUpdate();
            return rec;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update value history in SQLite", e);
        }
    }

    // Find one record by ID
    public ValueHistoryRecord findById(String id) {
        String sql = """
            SELECT id, objectId, timestamp, value
            FROM ValueHistory
            WHERE id = ?
        """;

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, id);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read ValueHistory by id from SQLite", e);
        }

        return null;
    }

    // Find all history records for objectId
    public List<ValueHistoryRecord> findByObjectId(String objectId) {
        String sql = """
            SELECT id, objectId, timestamp, value
            FROM ValueHistory
            WHERE objectId = ?
            ORDER BY timestamp DESC
        """;

        List<ValueHistoryRecord> list = new ArrayList<>();

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, objectId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read ValueHistory by objectId from SQLite", e);
        }

        return list;
    }

    public List<ValueHistoryRecord> findAll() {
        String sql = "SELECT id, objectId, timestamp, value FROM ValueHistory ORDER BY timestamp DESC";
        List<ValueHistoryRecord> list = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read value histories from SQLite", e);
        }
    }

    public void delete(String id) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM ValueHistory WHERE id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete value history from SQLite", e);
        }
    }

    // Row mapper
    private ValueHistoryRecord mapRow(ResultSet rs) throws SQLException {
        return new ValueHistoryRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("objectId")),
                LocalDateTime.parse(rs.getString("timestamp")),
                rs.getString("value") == null ? null : new java.math.BigDecimal(rs.getString("value"))
        );
    }
}

