package com.phase.api.auth;

import com.phase.api.usuario.UsuarioDTO;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        UsuarioDTO user
) {
}
