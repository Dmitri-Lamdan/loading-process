package com.loading.process.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.loading.process.model.EventLogRecord;
import com.loading.process.model.IntervalRecord;
import com.loading.process.model.ServiceTaskRecord;

public record ObjectResponse(
        String id,
        String name,
        String type,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastChangeDate,
        BigDecimal currentValue,
        LocalDate nextServiceDate,
        List<EventLogRecord> events,
        List<IntervalRecord> intervals,
        List<ServiceTaskRecord> serviceTasks) {
}

