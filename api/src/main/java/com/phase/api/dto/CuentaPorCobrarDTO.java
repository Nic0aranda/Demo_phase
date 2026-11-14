package com.phase.api.dto;

import java.math.BigDecimal;

public record CuentaPorCobrarDTO(
        String facturaId,
        String rutCliente,
        String cliente,
        String fechaEmision,
        String fechaVencimiento,
        BigDecimal total,
        BigDecimal pagado,
        BigDecimal creditos,
        BigDecimal montoPendiente,
        long diasAtraso,
        String estado
) {
}
