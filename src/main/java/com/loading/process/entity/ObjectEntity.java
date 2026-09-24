package com.loading.process.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.loading.process.model.ObjectStatus;

public class ObjectEntity {

    private String id;
    private String name;
    private String type;
    private ObjectStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastChangeDate;
    private BigDecimal currentValue;
    private LocalDate nextServiceDate;

    public ObjectEntity() {
    }

    public ObjectEntity(String id, String name, String type, ObjectStatus status,
            Instant createdAt, Instant updatedAt, Instant lastChangeDate,
            BigDecimal currentValue, LocalDate nextServiceDate) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastChangeDate = lastChangeDate;
        this.currentValue = currentValue;
        this.nextServiceDate = nextServiceDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ObjectStatus getStatus() {
        return status;
    }

    public void setStatus(ObjectStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastChangeDate() {
        return lastChangeDate;
    }

    public void setLastChangeDate(Instant lastChangeDate) {
        this.lastChangeDate = lastChangeDate;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public LocalDate getNextServiceDate() {
        return nextServiceDate;
    }

    public void setNextServiceDate(LocalDate nextServiceDate) {
        this.nextServiceDate = nextServiceDate;
    }
}
