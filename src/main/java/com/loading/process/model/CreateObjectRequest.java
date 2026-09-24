package com.loading.process.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateObjectRequest(
        String name,
        ObjectType type,
        ObjectStatus status,
        BigDecimal currentValue,
        LocalDate nextServiceDate) {

    public CreateObjectRequest(String name, String type, ObjectStatus status,
            BigDecimal currentValue, LocalDate nextServiceDate) {
        this(name, ObjectType.fromValue(type), status, currentValue, nextServiceDate);
    }
}
