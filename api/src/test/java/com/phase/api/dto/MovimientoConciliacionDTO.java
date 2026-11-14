package com.phase.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MovimientoConciliacionDTO(
        String id,
        String fecha,
        String concepto,

        @NotNull(message = "El monto es obligatorio")
        BigDecimal monto,

        String documentoErp,
        String estado,
        String origen
) {
}
