package com.phase.api.service;

import com.phase.api.dto.FacturaDTO;
import com.phase.api.dto.NotaCreditoDTO;
import com.phase.api.dto.PagoDTO;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Calculos de saldo de una factura a partir de sus pagos y notas de credito.
 * Se mantienen como funciones puras para evitar dependencias circulares entre
 * los servicios de cobranza.
 */
public final class SaldoCalculator {

    private SaldoCalculator() {
    }

    public static BigDecimal total(FacturaDTO factura) {
        return factura.getTotal() == null ? BigDecimal.ZERO : factura.getTotal();
    }

    public static BigDecimal pagado(String facturaId, Collection<PagoDTO> pagos) {
        if (pagos == null) {
            return BigDecimal.ZERO;
        }
        return pagos.stream()
                .filter(pago -> facturaId.equals(pago.facturaId()))
                .filter(pago -> !"ANULADO".equalsIgnoreCase(pago.estado()))
                .map(pago -> pago.monto() == null ? BigDecimal.ZERO : pago.monto())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal creditos(String facturaId, Collection<NotaCreditoDTO> notas) {
        if (notas == null) {
            return BigDecimal.ZERO;
        }
        return notas.stream()
                .filter(nota -> facturaId.equals(nota.facturaId()))
                .filter(nota -> !"ANULADA".equalsIgnoreCase(nota.estado()))
                .map(nota -> nota.montoTotal() == null ? BigDecimal.ZERO : nota.montoTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal pendiente(FacturaDTO factura,
                                       Collection<PagoDTO> pagos,
                                       Collection<NotaCreditoDTO> notas) {
        BigDecimal pendiente = total(factura)
                .subtract(pagado(factura.getId(), pagos))
                .subtract(creditos(factura.getId(), notas));
        return pendiente.signum() < 0 ? BigDecimal.ZERO : pendiente;
    }
}
