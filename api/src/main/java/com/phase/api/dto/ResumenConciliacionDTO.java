package com.phase.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResumenConciliacionDTO(
        BigDecimal saldoBanco,
        BigDecimal totalIngresos,
        BigDecimal totalEgresos,
        long movimientosPendientes,
        long movimientosConciliados,
        BigDecimal montoPendienteConciliar,
        String moneda,
        List<MovimientoConciliacionDTO> movimientos
) {
}
