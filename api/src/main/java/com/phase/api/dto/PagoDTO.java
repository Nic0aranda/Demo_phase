package com.phase.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoDTO(
        String id,

        @NotBlank(message = "La factura es obligatoria")
        String facturaId,

        String rutCliente,
        String cliente,
        String fecha,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        BigDecimal monto,

        @Pattern(regexp = "(?i)TRANSFERENCIA|CHEQUE|EFECTIVO|TARJETA|OTRO",
                message = "Medio de pago invalido")
        String medioPago,

        String referencia,
        String observacion,
        String estado
) {
}
