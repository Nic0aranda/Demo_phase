package com.phase.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record EnviarFacturaRequest(
        @Email(message = "El email del destinatario no es valido")
        @Size(max = 120, message = "El email del destinatario es demasiado largo")
        String destinatario,

        @Size(max = 160, message = "El asunto es demasiado largo")
        String asunto,

        @Size(max = 2000, message = "El mensaje es demasiado largo")
        String mensaje
) {
}
