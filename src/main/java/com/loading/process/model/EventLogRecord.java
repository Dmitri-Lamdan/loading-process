package com.loading.process.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventLogRecord(
        UUID id,
        UUID objectId,
        UUID userId,
        LocalDateTime timestamp,
        String eventType,
        String message
) {}