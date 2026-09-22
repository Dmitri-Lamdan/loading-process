package com.loading.process.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ObjectType {
    CALENDAR("calendar"),
    MILEAGE("mileage"),
    EVENT("event"),
    COMBINED("combined"),
    OTHER("other");

    private final String value;

    ObjectType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ObjectType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ObjectType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown object type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
