package com.phase.api.auth;

import com.phase.api.common.ApiException;
import com.phase.api.security.EstadoUsuario;
import com.phase.api.security.JwtService;
import com.phase.api.usuario.UsuarioDTO;
import com.phase.api.usuario.UsuarioRecord;
import com.phase.api.usuario.UsuarioService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioService usuarioService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        UsuarioRecord record = usuarioService.obtenerRegistro(request.email());

        if (record == null
                || record.getEstado() != EstadoUsuario.ACTIVO
                || !passwordEncoder.matches(request.password(), record.getPasswordHash())) {
            throw ApiException.unauthorized("Correo o contrasena incorrectos");
        }

        String token = jwtService.generate(record);
        return new LoginResponse(
                token,
                "Bearer",
                jwtService.expirationSeconds(),
                UsuarioDTO.from(record)
        );
    }

    public UsuarioDTO me(String email) {
        return usuarioService.obtener(email);
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        usuarioService.cambiarPassword(
                email,
                request.currentPassword(),
                request.newPassword(),
                request.confirmPassword()
        );
    }
}
