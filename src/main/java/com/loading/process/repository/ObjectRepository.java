package com.loading.process.repository;

import com.loading.process.model.ObjectResponse;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.loading.process.model.ValueHistoryRecord;
import com.loading.process.model.ObjectType;
import com.loading.process.model.ObjectStatus;
import org.springframework.stereotype.Repository;

@Repository
public class ObjectRepository {

    private final DatabaseConnectionProvider connectionProvider;

    public ObjectRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection connection = connectionProvider.getConnection();
                Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS objects (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        lastChangeDate TEXT NOT NULL,
                        currentValue TEXT,
                        nextServiceDate TEXT
                    )
                    """);
            statement.executeUpdate("""
                    UPDATE objects SET status = CASE
                        WHEN LOWER(status) IN ('planned', 'in_progress', 'inprogress', 'active') THEN 'active'
                        WHEN LOWER(status) IN ('completed', 'cancelled', 'inactive') THEN 'inactive'
                        ELSE status
                    END
                    WHERE LOWER(status) IN ('planned', 'in_progress', 'inprogress', 'completed', 'cancelled')
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite database", e);
        }
    }

    public List<ObjectResponse> findObjects(ObjectType type, ObjectStatus status) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, name, type, status, createdAt, updatedAt, lastChangeDate, currentValue, nextServiceDate FROM Object WHERE 1=1");
        List<String> params = new ArrayList<>();

        if (type != null) {
            sql.append(" AND type = ?");
            params.add(type.value());
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.value());
        }

        sql.append(" ORDER BY createdAt DESC");

        List<ObjectResponse> result = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read objects from SQLite", e);
        }
        return result;
    }

    public ObjectResponse findById(String id) {
        String sql = "SELECT id, name, type, status, createdAt, updatedAt, lastChangeDate, currentValue, nextServiceDate FROM Object WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read object by id from SQLite", e);
        }
        return null;
    }

    public void save(ObjectResponse object) {
        String sql = "INSERT INTO Object (\n" +
                "    id, name, type, status, createdAt, updatedAt, lastChangeDate, currentValue, nextServiceDate\n" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, object.id());
            statement.setString(2, object.name());
            statement.setString(3, object.type());
            statement.setString(4, object.status().value());
            statement.setString(5, Objects.toString(object.createdAt(), null));
            statement.setString(6, Objects.toString(object.updatedAt(), null));
            statement.setString(7, Objects.toString(object.lastChangeDate(), null));
            statement.setString(8, object.currentValue() == null ? null : object.currentValue().toPlainString());
            statement.setString(9, object.nextServiceDate() == null ? null : object.nextServiceDate().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save object to SQLite", e);
        }
    }

    public void update(ObjectResponse object) {
        String sql = "UPDATE Object SET name = ?, type = ?, status = ?, updatedAt = ?, lastChangeDate = ?, currentValue = ?, nextServiceDate = ? WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, object.name());
            statement.setString(2, object.type());
            statement.setString(3, object.status().value());
            statement.setString(4, Objects.toString(object.updatedAt(), null));
            statement.setString(5, Objects.toString(object.lastChangeDate(), null));
            statement.setString(6, object.currentValue() == null ? null : object.currentValue().toPlainString());
            statement.setString(7, object.nextServiceDate() == null ? null : object.nextServiceDate().toString());
            statement.setString(8, object.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update object in SQLite", e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM Object WHERE id = ?";

        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete object from SQLite", e);
        }
    }

    private ObjectResponse mapRow(ResultSet rs) throws SQLException {
        return new ObjectResponse(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("type"),
                ObjectStatus.fromValue(rs.getString("status")),
                Instant.parse(rs.getString("createdAt")),
                Instant.parse(rs.getString("updatedAt")),
                Instant.parse(rs.getString("lastChangeDate")),
                rs.getString("currentValue") == null ? null : new BigDecimal(rs.getString("currentValue")),
                rs.getString("nextServiceDate") == null ? null : LocalDate.parse(rs.getString("nextServiceDate")),
                java.util.List.of(), java.util.List.of(), java.util.List.of());
    }

}
