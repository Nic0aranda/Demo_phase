package com.phase.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public record AppUserPrincipal(
        String email,
        String nombre,
        Rol rol,
        List<String> permisos
) {

    public Collection<? extends GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (rol != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.name()));
        }
        if (permisos != null) {
            for (String permiso : permisos) {
                if (permiso == null || permiso.isBlank()) {
                    continue;
                }
                String normalized = permiso.trim()
                        .toUpperCase(Locale.ROOT)
                        .replace(' ', '_');
                authorities.add(new SimpleGrantedAuthority("PERM_" + normalized));
            }
        }
        return authorities;
    }
}
