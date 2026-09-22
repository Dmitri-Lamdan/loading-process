package com.loading.process.repository;

import com.loading.process.model.ServiceTaskRecord;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

@Repository
public class ServiceTaskRepository {

    private final DatabaseConnectionProvider connectionProvider;

    public ServiceTaskRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ServiceTask (
                        id TEXT PRIMARY KEY,
                        objectId TEXT NOT NULL,
                        plannedDate TEXT,
                        completedDate TEXT,
                        status TEXT,
                        comment TEXT
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize ServiceTask table", e);
        }
    }

    public ServiceTaskRecord save(ServiceTaskRecord rec) {
        String sql = "INSERT INTO ServiceTask (id, objectId, plannedDate, completedDate, status, comment) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rec.id().toString());
            statement.setString(2, rec.objectId().toString());
            statement.setString(3, rec.plannedDate() == null ? null : rec.plannedDate().toString());
            statement.setString(4, rec.completedDate() == null ? null : rec.completedDate().toString());
            statement.setString(5, rec.status());
            statement.setString(6, rec.comment());
            statement.executeUpdate();
            return rec;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save service task to SQLite", e);
        }
    }

    public ServiceTaskRecord update(ServiceTaskRecord rec) {
        String sql = "UPDATE ServiceTask SET objectId = ?, plannedDate = ?, completedDate = ?, status = ?, comment = ? WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rec.objectId().toString());
            statement.setString(2, rec.plannedDate() == null ? null : rec.plannedDate().toString());
            statement.setString(3, rec.completedDate() == null ? null : rec.completedDate().toString());
            statement.setString(4, rec.status());
            statement.setString(5, rec.comment());
            statement.setString(6, rec.id().toString());
            statement.executeUpdate();
            return rec;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update service task in SQLite", e);
        }
    }

    public ServiceTaskRecord findById(UUID id) {
        String sql = "SELECT id, objectId, plannedDate, completedDate, status, comment FROM ServiceTask WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read service task from SQLite", e);
        }
    }

    public List<ServiceTaskRecord> findByObjectId(UUID objectId) {
        String sql = "SELECT id, objectId, plannedDate, completedDate, status, comment FROM ServiceTask WHERE objectId = ? ORDER BY plannedDate DESC";
        List<ServiceTaskRecord> list = new ArrayList<>();
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
            throw new IllegalStateException("Failed to read service tasks from SQLite", e);
        }
    }

    public List<ServiceTaskRecord> findAll() {
        String sql = "SELECT id, objectId, plannedDate, completedDate, status, comment FROM ServiceTask ORDER BY plannedDate DESC";
        List<ServiceTaskRecord> list = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read service tasks from SQLite", e);
        }
    }

    public void delete(UUID id) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM ServiceTask WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete service task from SQLite", e);
        }
    }

    private ServiceTaskRecord mapRow(ResultSet rs) throws SQLException {
        String plannedDateStr = rs.getString("plannedDate");
        String completedDateStr = rs.getString("completedDate");
        return new ServiceTaskRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("objectId")),
                plannedDateStr == null ? null : LocalDate.parse(plannedDateStr),
                completedDateStr == null ? null : LocalDate.parse(completedDateStr),
                rs.getString("status"),
                rs.getString("comment")
        );
    }
}
