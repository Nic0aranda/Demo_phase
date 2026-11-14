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
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(UsuarioService usuarioService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LoginRateLimiter loginRateLimiter) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        loginRateLimiter.checkAllowed(request.email(), clientIp);

        UsuarioRecord record = usuarioService.obtenerRegistro(request.email());

        if (record == null
                || record.getEstado() != EstadoUsuario.ACTIVO
                || !passwordEncoder.matches(request.password(), record.getPasswordHash())) {
            loginRateLimiter.recordFailure(request.email(), clientIp);
            throw ApiException.unauthorized("Correo o contrasena incorrectos");
        }

        loginRateLimiter.recordSuccess(request.email(), clientIp);
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
