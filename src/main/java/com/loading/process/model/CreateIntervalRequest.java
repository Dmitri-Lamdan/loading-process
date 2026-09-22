package com.loading.process.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateIntervalRequest(
        UUID objectId,
        int intervalValue,
        IntervalUnit intervalUnit,
        LocalDateTime createdAt
) {}