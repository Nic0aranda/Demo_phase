package com.phase.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.validation")
public class ValidationProperties {

    /**
     * Si es true valida el digito verificador del RUT. En la demo se deja en
     * false para permitir los RUT de ejemplo del frontend.
     */
    private boolean rutDv = false;

    public boolean isRutDv() {
        return rutDv;
    }

    public void setRutDv(boolean rutDv) {
        this.rutDv = rutDv;
    }
}
