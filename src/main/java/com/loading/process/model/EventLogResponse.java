package com.loading.process.model;

public record EventLogResponse(
        EventLogRecord event,
        ObjectResponse object,
        UserRecord user
) {}