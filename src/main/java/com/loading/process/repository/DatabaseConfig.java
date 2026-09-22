package com.loading.process.repository;

import com.loading.process.repository.EventLogRepository;
import com.loading.process.repository.IntervalRepository;
import com.loading.process.repository.ServiceTaskRepository;
import com.loading.process.repository.UserRepository;
import com.loading.process.repository.ValueHistoryRepository;
import com.loading.process.repository.NotificationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {

    private static final String DB_URL = "jdbc:sqlite:D:/Development/Rust/kinkeep/data.db";

    @Bean
    public DatabaseConnectionProvider databaseConnectionProvider() {
        return new DatabaseConnectionProvider(DB_URL);
    }

    @Bean
    public EventLogRepository eventLogRepository(DatabaseConnectionProvider connectionProvider) {
        return new EventLogRepository(connectionProvider);
    }

    @Bean
    public IntervalRepository intervalRepository(DatabaseConnectionProvider connectionProvider) {
        return new IntervalRepository(connectionProvider);
    }

    @Bean
    public UserRepository userRepository(DatabaseConnectionProvider connectionProvider) {
        return new UserRepository(connectionProvider);
    }

    @Bean
    public ServiceTaskRepository serviceTaskRepository(DatabaseConnectionProvider connectionProvider) {
        return new ServiceTaskRepository(connectionProvider);
    }

    @Bean
    public ValueHistoryRepository valueHistoryRepository(DatabaseConnectionProvider connectionProvider) {
        return new ValueHistoryRepository(connectionProvider);
    }

    @Bean
    public NotificationRepository notificationRepository(DatabaseConnectionProvider connectionProvider, UserRepository userRepository) {
        return new NotificationRepository(connectionProvider, userRepository);
    }
}
