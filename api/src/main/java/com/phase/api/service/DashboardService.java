package com.phase.api.service;

import com.phase.api.config.FinanceProperties;
import com.phase.api.dto.ClienteDTO;
import com.phase.api.dto.DashboardDTO;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.dto.OperacionDTO;
import com.phase.api.dto.PagoDTO;
import com.phase.api.dto.PresupuestoDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

@Service
public class DashboardService {

    private static final Set<String> OPERACIONES_ACTIVAS =
            Set.of("PLANIFICADA", "EN_CURSO", "ATRASADO");
    private static final Set<String> PRESUPUESTOS_PENDIENTES =
            Set.of("BORRADOR", "ENVIADO");

    private final FacturaService facturaService;
    private final PagoService pagoService;
    private final ClienteService clienteService;
    private final OperacionService operacionService;
    private final PresupuestoService presupuestoService;
    private final CarteraService carteraService;
    private final FinanceProperties financeProperties;

    public DashboardService(FacturaService facturaService,
                            PagoService pagoService,
                            ClienteService clienteService,
                            OperacionService operacionService,
                            PresupuestoService presupuestoService,
                            CarteraService carteraService,
                            FinanceProperties financeProperties) {
        this.facturaService = facturaService;
        this.pagoService = pagoService;
        this.clienteService = clienteService;
        this.operacionService = operacionService;
        this.presupuestoService = presupuestoService;
        this.carteraService = carteraService;
        this.financeProperties = financeProperties;
    }

    public DashboardDTO obtener() {
        YearMonth mesActual = YearMonth.now();

        List<FacturaDTO> facturas = facturaService.listarTodas();
        List<PagoDTO> pagos = pagoService.listarTodos();
        List<ClienteDTO> clientes = clienteService.listarTodos();
        List<OperacionDTO> operaciones = operacionService.listarTodas();
        List<PresupuestoDTO> presupuestos = presupuestoService.listarTodos();

        BigDecimal facturadoMes = facturas.stream()
                .filter(factura -> !"ANULADA".equalsIgnoreCase(factura.getEstado()))
                .filter(factura -> enMes(factura.getFechaEmision(), mesActual))
                .map(SaldoCalculator::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pagosMes = pagos.stream()
                .filter(pago -> !"ANULADO".equalsIgnoreCase(pago.estado()))
                .filter(pago -> enMes(pago.fecha(), mesActual))
                .map(pago -> pago.monto() == null ? BigDecimal.ZERO : pago.monto())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long clientesActivos = clientes.stream().filter(ClienteDTO::isActivo).count();
        long operacionesActivas = operaciones.stream()
                .filter(operacion -> OPERACIONES_ACTIVAS.contains(normalizar(operacion.getEstado())))
                .count();
        long presupuestosPendientes = presupuestos.stream()
                .filter(presupuesto -> PRESUPUESTOS_PENDIENTES.contains(normalizar(presupuesto.getEstado())))
                .count();

        var cartera = carteraService.resumen();

        return new DashboardDTO(
                facturadoMes,
                cartera.totalPorCobrar(),
                cartera.totalVencido(),
                pagosMes,
                clientesActivos,
                operacionesActivas,
                presupuestosPendientes,
                cartera.facturasPendientes(),
                cartera.facturasVencidas(),
                cartera.facturasPagadas(),
                financeProperties.getCurrency(),
                financeProperties.getIvaRate(),
                cartera
        );
    }

    private boolean enMes(String fecha, YearMonth mes) {
        if (fecha == null || fecha.isBlank()) {
            return false;
        }
        try {
            return YearMonth.from(LocalDate.parse(fecha)).equals(mes);
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizar(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
