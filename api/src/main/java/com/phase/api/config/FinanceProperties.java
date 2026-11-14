package com.phase.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "erp.finance")
public class FinanceProperties {

    private String currency = "USD";
    private BigDecimal ivaRate = BigDecimal.ZERO;

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getIvaRate() {
        return ivaRate;
    }

    public void setIvaRate(BigDecimal ivaRate) {
        this.ivaRate = ivaRate;
    }
}
