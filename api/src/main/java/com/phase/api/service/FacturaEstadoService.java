package com.phase.api.service;

import com.phase.api.common.FacturaActualizadaEvent;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.dto.NotaCreditoDTO;
import com.phase.api.dto.PagoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mantiene el estado de la factura alineado con sus movimientos reales.
 *
 * <p>El estado deja de ser una verdad paralela: se deriva del saldo pendiente y
 * de la existencia de una nota de anulacion vigente. Las transiciones manuales
 * se validan en {@link FacturaService#actualizarEstado(String, String)}.</p>
 */
@Service
public class FacturaEstadoService {

    private static final Logger log = LoggerFactory.getLogger(FacturaEstadoService.class);

    private final FacturaService facturaService;
    private final PagoService pagoService;
    private final NotaCreditoService notaCreditoService;

    public FacturaEstadoService(FacturaService facturaService,
                                PagoService pagoService,
                                NotaCreditoService notaCreditoService) {
        this.facturaService = facturaService;
        this.pagoService = pagoService;
        this.notaCreditoService = notaCreditoService;
    }

    @EventListener
    public void onFacturaActualizada(FacturaActualizadaEvent event) {
        recalcular(event.facturaId());
    }

    public void recalcular(String facturaId) {
        FacturaDTO factura = facturaService.obtenerPorId(facturaId);
        List<PagoDTO> pagos = pagoService.listarTodos();
        List<NotaCreditoDTO> notas = notaCreditoService.listarPorFactura(facturaId);

        boolean anulacionVigente = notas.stream()
                .anyMatch(nota -> "EMITIDA".equalsIgnoreCase(nota.estado())
                        && "ANULACION".equalsIgnoreCase(nota.tipo()));

        if ("ANULADA".equalsIgnoreCase(factura.getEstado()) && !anulacionVigente) {
            // Anulacion manual: no la revertimos automaticamente.
            return;
        }

        String nuevo;
        if (anulacionVigente) {
            nuevo = "ANULADA";
        } else {
            BigDecimal pendiente = SaldoCalculator.pendiente(factura, pagos, notas);
            nuevo = pendiente.signum() <= 0 ? "PAGADA" : "PENDIENTE";
        }

        if (!nuevo.equalsIgnoreCase(factura.getEstado())) {
            log.info("Recalculando estado factura={} desde={} hacia={}",
                    facturaId, factura.getEstado(), nuevo);
            facturaService.cambiarEstadoSistema(facturaId, nuevo);
        }
    }
}
