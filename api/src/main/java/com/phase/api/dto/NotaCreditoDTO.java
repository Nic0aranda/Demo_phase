package com.phase.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NotaCreditoDTO(
        String id,

        @NotBlank(message = "La factura de origen es obligatoria")
        String facturaId,

        String rutCliente,
        String cliente,

        @NotBlank(message = "El tipo de nota de credito es obligatorio")
        @Pattern(regexp = "(?i)ANULACION|DESCUENTO|DEVOLUCION",
                message = "Tipo de nota de credito invalido")
        String tipo,

        BigDecimal montoNeto,
        BigDecimal montoTotal,

        @NotBlank(message = "El motivo es obligatorio")
        String motivo,

        String fechaEmision,
        String estado
) {
}
