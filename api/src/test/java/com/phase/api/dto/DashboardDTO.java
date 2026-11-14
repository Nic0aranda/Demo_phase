package com.phase.api.dto;

import java.math.BigDecimal;

public record DashboardDTO(
        BigDecimal facturadoMes,
        BigDecimal pendienteCobro,
        BigDecimal montoVencido,
        BigDecimal pagosMes,
        long clientesActivos,
        long operacionesActivas,
        long presupuestosPendientes,
        long facturasPendientes,
        long facturasVencidas,
        long facturasPagadas,
        String moneda,
        BigDecimal ivaRate,
        ResumenCarteraDTO cartera
) {
}
