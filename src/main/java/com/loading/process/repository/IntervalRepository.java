package com.loading.process.repository;

import com.loading.process.model.IntervalRecord;
import com.loading.process.model.IntervalUnit;

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
public class IntervalRepository {

    private final DatabaseConnectionProvider connectionProvider;

    public IntervalRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS Interval (
                        id TEXT PRIMARY KEY,
                        objectId TEXT NOT NULL,
                        intervalValue INTEGER NOT NULL,
                        intervalUnit TEXT NOT NULL,
                        createdAt TEXT NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize Interval table", e);
        }
    }

    public IntervalRecord save(IntervalRecord rec) {
        String sql = "INSERT INTO Interval (id, objectId, intervalValue, intervalUnit, createdAt) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rec.id().toString());
            statement.setString(2, rec.objectId().toString());
            statement.setInt(3, rec.intervalValue());
            statement.setString(4, rec.intervalUnit() == null ? null : rec.intervalUnit().name());
            statement.setString(5, rec.createdAt().toString());
            statement.executeUpdate();
            return rec;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save interval to SQLite", e);
        }
    }

    public IntervalRecord update(IntervalRecord rec) {
        String sql = "UPDATE Interval SET objectId = ?, intervalValue = ?, intervalUnit = ?, createdAt = ? WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rec.objectId().toString());
            statement.setInt(2, rec.intervalValue());
            statement.setString(3, rec.intervalUnit() == null ? null : rec.intervalUnit().name());
            statement.setString(4, rec.createdAt().toString());
            statement.setString(5, rec.id().toString());
            statement.executeUpdate();
            return rec;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update interval in SQLite", e);
        }
    }

    public IntervalRecord findById(UUID id) {
        String sql = "SELECT id, objectId, intervalValue, intervalUnit, createdAt FROM Interval WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read interval from SQLite", e);
        }
    }

    public List<IntervalRecord> findByObjectId(UUID objectId) {
        String sql = "SELECT id, objectId, intervalValue, intervalUnit, createdAt FROM Interval WHERE objectId = ? ORDER BY createdAt DESC";
        List<IntervalRecord> list = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, objectId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read intervals from SQLite", e);
        }
    }

    public List<IntervalRecord> findAll() {
        String sql = "SELECT id, objectId, intervalValue, intervalUnit, createdAt FROM Interval ORDER BY createdAt DESC";
        List<IntervalRecord> list = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read intervals from SQLite", e);
        }
    }

    public void delete(UUID id) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM Interval WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete interval from SQLite", e);
        }
    }

    private IntervalRecord mapRow(ResultSet rs) throws SQLException {
        String unit = rs.getString("intervalUnit");
        return new IntervalRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("objectId")),
                rs.getInt("intervalValue"),
                unit == null ? null : IntervalUnit.valueOf(unit),
                LocalDateTime.parse(rs.getString("createdAt"))
        );
    }
}
