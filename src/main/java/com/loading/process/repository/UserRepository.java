package com.loading.process.repository;

import com.loading.process.model.UserRecord;
import com.loading.process.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final DatabaseConnectionProvider connectionProvider;

    public UserRepository(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS User (
                        id TEXT PRIMARY KEY,
                        username TEXT NOT NULL UNIQUE,
                        email TEXT,
                        role TEXT
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize User table", e);
        }
    }

    public UserRecord save(UserRecord user) {
        String sql = "INSERT INTO User (id, username, email, role) VALUES (?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.id().toString());
            statement.setString(2, user.username());
            statement.setString(3, user.email());
            statement.setString(4, user.role() == null ? null : user.role().name());
            statement.executeUpdate();
            return user;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save user to SQLite", e);
        }
    }

    public UserRecord update(UserRecord user) {
        String sql = "UPDATE User SET username = ?, email = ?, role = ? WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.username());
            statement.setString(2, user.email());
            statement.setString(3, user.role() == null ? null : user.role().name());
            statement.setString(4, user.id().toString());
            statement.executeUpdate();
            return user;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update user in SQLite", e);
        }
    }

    public UserRecord findById(UUID id) {
        String sql = "SELECT id, username, email, role FROM User WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read user from SQLite", e);
        }
        return null;
    }

    public List<UserRecord> findAll() {
        String sql = "SELECT id, username, email, role FROM User";
        List<UserRecord> users = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read users from SQLite", e);
        }
    }

    public void delete(UUID id) {
        String sql = "DELETE FROM User WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete user from SQLite", e);
        }
    }

    private UserRecord mapRow(ResultSet rs) throws SQLException {
        String role = rs.getString("role");
        return new UserRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("username"),
                rs.getString("email"),
                role == null ? null : UserRole.valueOf(role)
        );
    }
}
