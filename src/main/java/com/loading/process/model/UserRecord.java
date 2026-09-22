package com.loading.process.model;

import java.util.UUID;

public record UserRecord(
        UUID id,
        String username,
        String email,
        UserRole role
) {}
