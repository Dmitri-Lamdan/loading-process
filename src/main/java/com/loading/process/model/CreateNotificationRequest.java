package com.loading.process.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateNotificationRequest(
        UUID objectId,
        UUID userId,
        String message,
        boolean isRead
) {}
