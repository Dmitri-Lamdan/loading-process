package com.loading.process.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateObjectRequest(
        String name,
        ObjectType type,
        String status,
        BigDecimal currentValue,
        LocalDate nextServiceDate) {

    public UpdateObjectRequest(String name, String type, String status,
            BigDecimal currentValue, LocalDate nextServiceDate) {
        this(name, type == null ? null : ObjectType.fromValue(type), status, currentValue, nextServiceDate);
    }
}
