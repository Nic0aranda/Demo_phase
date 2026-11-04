package com.phase.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecordatorioDTO(
        String id,

        @NotBlank(message = "La factura es obligatoria")
        String facturaId,

        String rutCliente,
        String cliente,

        @Pattern(regexp = "(?i)EMAIL|WHATSAPP", message = "Canal invalido")
        String canal,

        String asunto,
        String mensaje,
        String fechaEnvio,
        String estado
) {
}
