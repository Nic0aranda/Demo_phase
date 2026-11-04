package com.phase.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no es valido")
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        String password
) {
}
