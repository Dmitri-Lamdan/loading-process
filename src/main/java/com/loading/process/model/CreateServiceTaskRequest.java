package com.loading.process.model;

import java.time.LocalDate;
import java.util.UUID;

public record CreateServiceTaskRequest(
        UUID objectId,
        LocalDate plannedDate,
        LocalDate completedDate,
        String status,
        String comment
) {}
