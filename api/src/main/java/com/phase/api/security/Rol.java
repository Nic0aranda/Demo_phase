package com.phase.api.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum Rol {
    ADMIN,
    COMERCIAL,
    OPERACIONES,
    CONTABILIDAD;

    @JsonCreator
    public static Rol from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Rol.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
