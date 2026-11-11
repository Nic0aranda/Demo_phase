package com.phase.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.seed")
public class SeedProperties {

    private boolean enabled = true;
    private String adminEmail = "admin@empresa.com";
    private String adminPassword;
    private String adminName = "Administrador";
    private String adminApellidos = "ERP";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getAdminName() {
        return adminName;
    }

    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }

    public String getAdminApellidos() {
        return adminApellidos;
    }

    public void setAdminApellidos(String adminApellidos) {
        this.adminApellidos = adminApellidos;
    }
}
