package com.loading.process.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record  ValueHistoryRecord (
    UUID id,
    UUID objectId,
    LocalDateTime timestamp,
    BigDecimal value
) {}
