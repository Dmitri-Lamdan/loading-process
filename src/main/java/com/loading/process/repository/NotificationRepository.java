package com.loading.process.repository;

import com.loading.process.model.NotificationRecord;
import com.loading.process.model.NotificationStatus;
import com.loading.process.model.UserRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

    private final DatabaseConnectionProvider connectionProvider;
    private final UserRepository userRepository;

    public NotificationRepository(DatabaseConnectionProvider connectionProvider, UserRepository userRepository) {
        this.connectionProvider = connectionProvider;
        this.userRepository = userRepository;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection connection = connectionProvider.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS Notification (
                        id TEXT PRIMARY KEY,
                        objectId TEXT NOT NULL,
                        userId TEXT,
                        createdAt TEXT NOT NULL,
                        message TEXT,
                        isRead INTEGER DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'pending'
                    )
                    """);
            boolean hasStatusColumn = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(Notification)")) {
                while (columns.next()) {
                    if ("status".equalsIgnoreCase(columns.getString("name"))) {
                        hasStatusColumn = true;
                        break;
                    }
                }
            }
            if (!hasStatusColumn) {
                statement.executeUpdate(
                        "ALTER TABLE Notification ADD COLUMN status TEXT NOT NULL DEFAULT 'pending'");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize Notification table", e);
        }
    }

    public NotificationRecord save(NotificationRecord rec) {
        String sql = "INSERT INTO Notification (id, objectId, userId, createdAt, message, status, isRead) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rec.id().toString());
            statement.setString(2, rec.objectId().toString());
            statement.setString(3, rec.userId() == null ? null : rec.userId().toString());
            statement.setString(4, rec.createdAt().toString());
            statement.setString(5, rec.message());
            statement.setString(6, rec.status().value());
            statement.setInt(7, rec.isRead() ? 1 : 0);
            statement.executeUpdate();
            return rec;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save notification to SQLite", e);
        }
    }

    public NotificationRecord update(NotificationRecord rec) {
        String sql = "UPDATE Notification SET objectId = ?, userId = ?, createdAt = ?, message = ?, status = ?, isRead = ? WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rec.objectId().toString());
            statement.setString(2, rec.userId() == null ? null : rec.userId().toString());
            statement.setString(3, rec.createdAt().toString());
            statement.setString(4, rec.message());
            statement.setString(5, rec.status().value());
            statement.setInt(6, rec.isRead() ? 1 : 0);
            statement.setString(7, rec.id().toString());
            statement.executeUpdate();
            return rec;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update notification in SQLite", e);
        }
    }

    public NotificationRecord findById(UUID id) {
        String sql = "SELECT id, objectId, userId, createdAt, message, status, isRead FROM Notification WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read notification from SQLite", e);
        }
    }

    public List<NotificationRecord> findByObjectId(UUID objectId) {
        String sql = "SELECT id, objectId, userId, createdAt, message, status, isRead FROM Notification WHERE objectId = ? ORDER BY createdAt DESC";
        List<NotificationRecord> list = new ArrayList<>();
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
            throw new IllegalStateException("Failed to read notifications from SQLite", e);
        }
    }

    public List<NotificationRecord> findByUserId(UUID userId) {
        String sql = "SELECT id, objectId, userId, createdAt, message, status, isRead FROM Notification WHERE userId = ? ORDER BY createdAt DESC";
        List<NotificationRecord> list = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read notifications from SQLite", e);
        }
    }

    public List<NotificationRecord> findAll() {
        String sql = "SELECT id, objectId, userId, createdAt, message, status, isRead FROM Notification ORDER BY createdAt DESC";
        List<NotificationRecord> list = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read notifications from SQLite", e);
        }
    }

    public void delete(UUID id) {
        try (Connection connection = connectionProvider.getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM Notification WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete notification from SQLite", e);
        }
    }

    private NotificationRecord mapRow(ResultSet rs) throws SQLException {
        String userIdStr = rs.getString("userId");
        UUID userId = userIdStr == null ? null : UUID.fromString(userIdStr);

        UserRecord user = null;
        if (userId != null && userRepository != null) {
            user = userRepository.findById(userId);
        }

        return new NotificationRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("objectId")),
                userId,
                LocalDateTime.parse(rs.getString("createdAt")),
                rs.getString("message"),
                NotificationStatus.fromValue(rs.getString("status")),
                rs.getInt("isRead") == 1,
                user);
    }
}
