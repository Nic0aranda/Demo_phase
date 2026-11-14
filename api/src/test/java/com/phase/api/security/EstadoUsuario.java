package com.phase.api.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum EstadoUsuario {
    ACTIVO,
    INACTIVO;

    @JsonCreator
    public static EstadoUsuario from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return EstadoUsuario.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
