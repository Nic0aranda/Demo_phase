package com.phase.api.auth;

import com.phase.api.common.ApiException;
import com.phase.api.config.SecurityProperties;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita intentos fallidos de login por correo + IP. Es una proteccion local
 * de una sola instancia; para varias instancias se necesitaria un almacen
 * compartido.
 */
@Service
public class LoginRateLimiter {

    private final SecurityProperties securityProperties;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public void checkAllowed(String email, String clientIp) {
        String key = key(email, clientIp);
        if (key == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }

        if (attempt.lockedUntil() > now) {
            long retryAfter = Math.max(1L, (attempt.lockedUntil() - now + 999L) / 1000L);
            throw ApiException.tooManyRequests(
                    "LOGIN_BLOQUEADO",
                    "Demasiados intentos de inicio de sesion. Intenta nuevamente en "
                            + retryAfter + " segundos.",
                    retryAfter);
        }

        if (attempt.windowStart() + windowMillis() <= now) {
            attempts.remove(key, attempt);
        }
    }

    public void recordFailure(String email, String clientIp) {
        String key = key(email, clientIp);
        if (key == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long window = windowMillis();
        long lockout = lockoutMillis();
        int maxAttempts = maxAttempts();

        attempts.compute(key, (ignored, current) -> {
            Attempt base = current;
            if (base == null || now - base.windowStart() >= window) {
                base = new Attempt(0, now, 0L);
            }
            int failures = base.failures() + 1;
            long lockedUntil = failures >= maxAttempts ? now + lockout : 0L;
            return new Attempt(failures, base.windowStart(), lockedUntil);
        });

        purgeIfNeeded(now);
    }

    public void recordSuccess(String email, String clientIp) {
        String key = key(email, clientIp);
        if (key != null) {
            attempts.remove(key);
        }
    }

    private void purgeIfNeeded(long now) {
        if (attempts.size() < 1_000) {
            return;
        }
        long window = windowMillis();
        attempts.entrySet().removeIf(entry -> {
            Attempt attempt = entry.getValue();
            long expires = Math.max(attempt.windowStart() + window, attempt.lockedUntil());
            return expires <= now;
        });
    }

    private String key(String email, String clientIp) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = clientIp == null ? "" : clientIp.trim();
        return normalizedEmail + "|" + normalizedIp;
    }

    private int maxAttempts() {
        int configured = securityProperties.getLogin() == null
                ? 5
                : securityProperties.getLogin().getMaxAttempts();
        return Math.max(1, configured);
    }

    private long windowMillis() {
        long configured = securityProperties.getLogin() == null
                || securityProperties.getLogin().getWindow() == null
                ? 15 * 60_000L
                : securityProperties.getLogin().getWindow().toMillis();
        return Math.max(1_000L, configured);
    }

    private long lockoutMillis() {
        long configured = securityProperties.getLogin() == null
                || securityProperties.getLogin().getLockout() == null
                ? 15 * 60_000L
                : securityProperties.getLogin().getLockout().toMillis();
        return Math.max(1_000L, configured);
    }

    private record Attempt(int failures, long windowStart, long lockedUntil) {
    }
}
