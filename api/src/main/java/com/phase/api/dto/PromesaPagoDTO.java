package com.phase.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromesaPagoDTO(
        String id,

        @NotBlank(message = "La factura es obligatoria")
        String facturaId,

        String rutCliente,
        String cliente,

        @NotBlank(message = "La fecha de la promesa es obligatoria")
        String fechaPromesa,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        BigDecimal monto,

        String nota,
        String fechaRegistro,
        String estado
) {
}
