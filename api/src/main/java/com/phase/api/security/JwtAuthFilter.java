package com.phase.api.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.phase.api.usuario.UsuarioEstadoService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioEstadoService usuarioEstadoService;

    public JwtAuthFilter(JwtService jwtService, UsuarioEstadoService usuarioEstadoService) {
        this.jwtService = jwtService;
        this.usuarioEstadoService = usuarioEstadoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                Claims claims = jwtService.parse(token);
                String email = claims.getSubject();

                if (!usuarioEstadoService.estaActivo(email)) {
                    SecurityContextHolder.clearContext();
                    request.setAttribute("jwt.error", "USUARIO_INACTIVO");
                } else {
                    String nombre = claims.get("nombre", String.class);
                    Rol rol = Rol.valueOf(claims.get("rol", String.class));

                    @SuppressWarnings("unchecked")
                    List<String> permisos = claims.get("permisos", List.class);

                    AppUserPrincipal principal = new AppUserPrincipal(email, nombre, rol, permisos);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute("jwt.error", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
