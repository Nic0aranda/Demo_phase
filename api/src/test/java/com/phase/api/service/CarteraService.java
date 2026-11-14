package com.phase.api.service;

import com.phase.api.config.FinanceProperties;
import com.phase.api.dto.CuentaPorCobrarDTO;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.dto.NotaCreditoDTO;
import com.phase.api.dto.PagoDTO;
import com.phase.api.dto.ResumenCarteraDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class CarteraService {

    private final FacturaService facturaService;
    private final PagoService pagoService;
    private final NotaCreditoService notaCreditoService;
    private final ClienteService clienteService;
    private final FinanceProperties financeProperties;

    public CarteraService(FacturaService facturaService,
                          PagoService pagoService,
                          NotaCreditoService notaCreditoService,
                          ClienteService clienteService,
                          FinanceProperties financeProperties) {
        this.facturaService = facturaService;
        this.pagoService = pagoService;
        this.notaCreditoService = notaCreditoService;
        this.clienteService = clienteService;
        this.financeProperties = financeProperties;
    }

    public List<CuentaPorCobrarDTO> listar() {
        List<FacturaDTO> facturas = facturaService.listarTodas();
        List<PagoDTO> pagos = pagoService.listarTodos();
        List<NotaCreditoDTO> notas = notaCreditoService.listar();
        Map<String, String> nombres = clienteService.nombresPorDocumento();
        LocalDate hoy = LocalDate.now();

        List<CuentaPorCobrarDTO> cuentas = new ArrayList<>();
        for (FacturaDTO factura : facturas) {
            if ("ANULADA".equalsIgnoreCase(factura.getEstado())) {
                continue;
            }

            BigDecimal pagado = SaldoCalculator.pagado(factura.getId(), pagos);
            BigDecimal creditos = SaldoCalculator.creditos(factura.getId(), notas);
            BigDecimal pendiente = SaldoCalculator.pendiente(factura, pagos, notas);
            LocalDate vencimiento = vencimiento(factura);
            long diasAtraso = vencimiento != null && vencimiento.isBefore(hoy)
                    ? ChronoUnit.DAYS.between(vencimiento, hoy)
                    : 0;
            String estado = estado(pendiente, vencimiento, diasAtraso, hoy);

            cuentas.add(new CuentaPorCobrarDTO(
                    factura.getId(),
                    factura.getRutCliente(),
                    nombres.getOrDefault(factura.getRutCliente(), factura.getRutCliente()),
                    factura.getFechaEmision(),
                    vencimiento == null ? null : vencimiento.toString(),
                    SaldoCalculator.total(factura),
                    pagado,
                    creditos,
                    pendiente,
                    diasAtraso,
                    estado
            ));
        }

        cuentas.sort(Comparator
                .comparingInt(CarteraService::severidad).reversed()
                .thenComparing(CuentaPorCobrarDTO::fechaVencimiento,
                        Comparator.nullsLast(String::compareTo)));
        return cuentas;
    }

    public ResumenCarteraDTO resumen() {
        List<CuentaPorCobrarDTO> cuentas = listar();

        BigDecimal porCobrar = BigDecimal.ZERO;
        BigDecimal vencido = BigDecimal.ZERO;
        BigDecimal porVencer = BigDecimal.ZERO;
        long pendientes = 0;
        long vencidas = 0;
        long pagadas = 0;

        for (CuentaPorCobrarDTO cuenta : cuentas) {
            if ("PAGADA".equals(cuenta.estado())) {
                pagadas++;
                continue;
            }
            pendientes++;
            porCobrar = porCobrar.add(cuenta.montoPendiente());
            if ("ATRASADO".equals(cuenta.estado()) || "CRITICO".equals(cuenta.estado())) {
                vencidas++;
                vencido = vencido.add(cuenta.montoPendiente());
            } else if ("POR_VENCER".equals(cuenta.estado())) {
                porVencer = porVencer.add(cuenta.montoPendiente());
            }
        }

        return new ResumenCarteraDTO(
                porCobrar,
                vencido,
                porVencer,
                pendientes,
                vencidas,
                pagadas,
                financeProperties.getCurrency()
        );
    }

    public CuentaPorCobrarDTO obtener(String facturaId) {
        return listar().stream()
                .filter(cuenta -> cuenta.facturaId().equalsIgnoreCase(facturaId))
                .findFirst()
                .orElseThrow(() -> com.phase.api.common.ApiException.notFound(
                        "Cuenta por cobrar no encontrada: " + facturaId));
    }

    private String estado(BigDecimal pendiente, LocalDate vencimiento, long diasAtraso, LocalDate hoy) {
        if (pendiente.signum() <= 0) {
            return "PAGADA";
        }
        if (vencimiento == null) {
            return "AL_DIA";
        }
        if (diasAtraso > 60) {
            return "CRITICO";
        }
        if (diasAtraso > 0) {
            return "ATRASADO";
        }
        long diasParaVencer = ChronoUnit.DAYS.between(hoy, vencimiento);
        if (diasParaVencer <= 7) {
            return "POR_VENCER";
        }
        return "AL_DIA";
    }

    private LocalDate vencimiento(FacturaDTO factura) {
        if (factura.getFechaVencimiento() != null && !factura.getFechaVencimiento().isBlank()) {
            try {
                return LocalDate.parse(factura.getFechaVencimiento());
            } catch (Exception ignored) {
                // se calcula desde la fecha de emision
            }
        }
        LocalDate emision = null;
        if (factura.getFechaEmision() != null && !factura.getFechaEmision().isBlank()) {
            try {
                emision = LocalDate.parse(factura.getFechaEmision());
            } catch (Exception ignored) {
                // sin fecha valida
            }
        }
        if (emision == null) {
            return null;
        }
        String condicion = factura.getCondicionPago() == null
                ? "NETO_30"
                : factura.getCondicionPago().toUpperCase();
        int dias = switch (condicion) {
            case "CONTADO" -> 0;
            case "NETO_60" -> 60;
            default -> 30;
        };
        return emision.plusDays(dias);
    }

    private static int severidad(CuentaPorCobrarDTO cuenta) {
        return switch (cuenta.estado()) {
            case "CRITICO" -> 5;
            case "ATRASADO" -> 4;
            case "POR_VENCER" -> 3;
            case "AL_DIA" -> 2;
            case "PAGADA" -> 1;
            default -> 0;
        };
    }
}
