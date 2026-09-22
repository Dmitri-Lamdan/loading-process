package com.loading.process.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationRecord(
        UUID id,
        UUID objectId,
        UUID userId,
        LocalDateTime createdAt,
        String message,
        boolean isRead,
        UserRecord user
) {}
