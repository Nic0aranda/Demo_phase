package com.phase.api.dto;

import java.math.BigDecimal;

public record ResumenCarteraDTO(
        BigDecimal totalPorCobrar,
        BigDecimal totalVencido,
        BigDecimal totalPorVencer,
        long facturasPendientes,
        long facturasVencidas,
        long facturasPagadas,
        String moneda
) {
}
