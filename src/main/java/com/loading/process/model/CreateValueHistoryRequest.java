package com.loading.process.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateValueHistoryRequest(
        UUID objectId,
        LocalDateTime timestamp,
        BigDecimal value
) {}
