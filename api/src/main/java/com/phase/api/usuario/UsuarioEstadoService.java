package com.phase.api.usuario;

import tools.jackson.databind.ObjectMapper;
import com.phase.api.config.SecurityProperties;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseException;
import com.phase.api.phase.PhaseJson;
import com.phase.api.security.EstadoUsuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Revalida el estado del usuario contra ^USUARIOS sin golpear PhaseDB en cada
 * peticion. La cache se invalida desde UsuarioService al mutar un usuario.
 */
@Service
public class UsuarioEstadoService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioEstadoService.class);

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public UsuarioEstadoService(PhaseClient phaseClient,
                                ObjectMapper objectMapper,
                                SecurityProperties securityProperties) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.securityProperties = securityProperties;
    }

    public boolean estaActivo(String email) {
        String key = normalizar(email);
        if (key == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(key);
        if (cached != null && now < cached.expiresAt()) {
            return cached.activo();
        }

        try {
            String json = phaseClient.read(UsuarioService.GLOBAL, key);
            UsuarioRecord record = PhaseJson.toObject(json, UsuarioRecord.class, objectMapper);
            boolean activo = record != null && record.getEstado() == EstadoUsuario.ACTIVO;
            long ttl = ttlMillis();
            long stale = staleMillis();
            cache.put(key, new CacheEntry(activo, now + ttl, now + ttl + stale));
            return activo;
        } catch (PhaseException e) {
            if (cached != null && now < cached.staleUntil()) {
                log.warn("No se pudo revalidar el usuario {}; se usa el ultimo estado conocido: {}",
                        key, e.getMessage());
                return cached.activo();
            }
            log.error("No se pudo revalidar el usuario {} y no hay estado de respaldo: {}",
                    key, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error inesperado al revalidar el usuario {}: {}", key, e.getMessage(), e);
            return false;
        }
    }

    public void invalidar(String email) {
        String key = normalizar(email);
        if (key != null) {
            cache.remove(key);
        }
    }

    private long ttlMillis() {
        long ttl = securityProperties.getUserCacheTtl() == null
                ? 30_000L
                : securityProperties.getUserCacheTtl().toMillis();
        return Math.max(1_000L, ttl);
    }

    private long staleMillis() {
        long stale = securityProperties.getUserCacheStale() == null
                ? 300_000L
                : securityProperties.getUserCacheStale().toMillis();
        return Math.max(0L, stale);
    }

    private String normalizar(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record CacheEntry(boolean activo, long expiresAt, long staleUntil) {
    }
}
