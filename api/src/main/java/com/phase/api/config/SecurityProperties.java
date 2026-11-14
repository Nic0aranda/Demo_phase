package com.phase.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "erp.security")
public class SecurityProperties {

    /**
     * Deja Swagger/OpenAPI accesible sin autenticacion. Solo debe activarse en dev.
     */
    private boolean swaggerPublic = false;

    /**
     * Tiempo durante el cual se reutiliza el estado ACTIVO/INACTIVO de un usuario.
     */
    private Duration userCacheTtl = Duration.ofSeconds(30);

    /**
     * Tiempo extra que se conserva el ultimo estado conocido si PhaseDB no responde.
     */
    private Duration userCacheStale = Duration.ofMinutes(5);

    private Login login = new Login();

    public boolean isSwaggerPublic() {
        return swaggerPublic;
    }

    public void setSwaggerPublic(boolean swaggerPublic) {
        this.swaggerPublic = swaggerPublic;
    }

    public Duration getUserCacheTtl() {
        return userCacheTtl;
    }

    public void setUserCacheTtl(Duration userCacheTtl) {
        this.userCacheTtl = userCacheTtl;
    }

    public Duration getUserCacheStale() {
        return userCacheStale;
    }

    public void setUserCacheStale(Duration userCacheStale) {
        this.userCacheStale = userCacheStale;
    }

    public Login getLogin() {
        return login;
    }

    public void setLogin(Login login) {
        this.login = login;
    }

    public static class Login {

        private int maxAttempts = 5;
        private Duration window = Duration.ofMinutes(15);
        private Duration lockout = Duration.ofMinutes(15);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public Duration getLockout() {
            return lockout;
        }

        public void setLockout(Duration lockout) {
            this.lockout = lockout;
        }
    }
}
