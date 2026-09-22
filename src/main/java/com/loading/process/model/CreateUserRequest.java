package com.loading.process.model;

public record CreateUserRequest(
        String username,
        String email,
        UserRole role
) {}
