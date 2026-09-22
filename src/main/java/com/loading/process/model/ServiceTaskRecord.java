package com.loading.process.model;

import java.time.LocalDate;
import java.util.UUID;

public record ServiceTaskRecord(
        UUID id,
        UUID objectId,
        LocalDate plannedDate,
        LocalDate completedDate,
        String status,
        String comment
) {}