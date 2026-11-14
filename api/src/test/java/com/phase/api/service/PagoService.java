package com.phase.api.service;

import com.phase.api.common.ApiException;
import com.phase.api.common.FacturaActualizadaEvent;
import com.phase.api.common.FechaUtil;
import com.phase.api.common.PhaseLockService;
import com.phase.api.common.SequenceService;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.dto.PagoDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import com.phase.api.phase.WriteOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    public static final String GLOBAL = "PAGOS";

    private static final Set<String> MEDIOS =
            Set.of("TRANSFERENCIA", "CHEQUE", "EFECTIVO", "TARJETA", "OTRO");
    private static final Set<String> ESTADOS = Set.of("REGISTRADO", "ANULADO");

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final SequenceService sequenceService;
    private final FacturaService facturaService;
    private final NotaCreditoService notaCreditoService;
    private final ClienteService clienteService;
    private final PhaseLockService lockService;
    private final ApplicationEventPublisher eventPublisher;

    public PagoService(PhaseClient phaseClient,
                       ObjectMapper objectMapper,
                       SequenceService sequenceService,
                       FacturaService facturaService,
                       NotaCreditoService notaCreditoService,
                       ClienteService clienteService,
                       PhaseLockService lockService,
                       ApplicationEventPublisher eventPublisher) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.sequenceService = sequenceService;
        this.facturaService = facturaService;
        this.notaCreditoService = notaCreditoService;
        this.clienteService = clienteService;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
    }

    public PagoDTO registrar(PagoDTO dto) {
        return lockService.conLock("FACTURA:" + dto.facturaId(), () -> {
            FacturaDTO factura = facturaService.obtenerPorId(dto.facturaId());
            if ("ANULADA".equalsIgnoreCase(factura.getEstado())) {
                throw ApiException.conflict("FACTURA_ANULADA", "No se pueden registrar pagos de una factura anulada");
            }

            BigDecimal monto = dto.monto().setScale(2, RoundingMode.HALF_UP);
            BigDecimal pendiente = calcularPendiente(factura);
            if (monto.compareTo(pendiente) > 0) {
                throw ApiException.unprocessable("MONTO_EXCEDE_SALDO",
                        "El pago supera el saldo pendiente de la factura",
                        Map.of("monto", "Saldo pendiente: " + pendiente));
            }

            LocalDate fecha = FechaUtil.opcional(dto.fecha(), "fecha", LocalDate.now());
            PagoDTO pago = new PagoDTO(
                    sequenceService.nextId("PAG"),
                    factura.getId(),
                    factura.getRutCliente(),
                    clienteService.nombreDe(factura.getRutCliente()),
                    fecha.toString(),
                    monto,
                    normalizarMedio(dto.medioPago()),
                    trim(dto.referencia()),
                    trim(dto.observacion()),
                    "REGISTRADO"
            );

            String estadoFactura = pendiente.subtract(monto).signum() <= 0 ? "PAGADA" : "PENDIENTE";
            try {
                phaseClient.writeJsonBatch(List.of(
                        WriteOp.replace(GLOBAL, pago.id(), PhaseJson.toJson(pago, objectMapper)),
                        WriteOp.merge(FacturaService.GLOBAL, factura.getId(),
                                "{\"estado\":\"" + estadoFactura + "\"}")
                ));
            } catch (RuntimeException e) {
                compensarPago(pago.id(), e);
                throw e;
            }
            return pago;
        });
    }

    public List<PagoDTO> listarTodos() {
        Map<String, PagoDTO> pagos =
                PhaseJson.toMap(phaseClient.readAll(GLOBAL), PagoDTO.class, objectMapper);
        return new ArrayList<>(pagos.values());
    }

    public List<PagoDTO> listarPorFactura(String facturaId) {
        String key = facturaId == null ? "" : facturaId.trim();
        return listarTodos().stream()
                .filter(pago -> key.equalsIgnoreCase(pago.facturaId()))
                .sorted(Comparator.comparing(PagoDTO::fecha, Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
    }

    public PagoDTO obtener(String id) {
        String json = phaseClient.read(GLOBAL, id);
        PagoDTO pago = PhaseJson.toObject(json, PagoDTO.class, objectMapper);
        if (pago == null) {
            throw ApiException.notFound("Pago no encontrado: " + id);
        }
        return pago;
    }

    public PagoDTO anular(String id) {
        PagoDTO pago = obtener(id);
        return lockService.conLock("FACTURA:" + pago.facturaId(), () -> {
            PagoDTO actual = obtener(id);
            if (!"REGISTRADO".equalsIgnoreCase(actual.estado())) {
                throw ApiException.conflict("PAGO_YA_ANULADO", "El pago ya esta anulado");
            }

            PagoDTO anulado = new PagoDTO(
                    actual.id(), actual.facturaId(), actual.rutCliente(), actual.cliente(), actual.fecha(),
                    actual.monto(), actual.medioPago(), actual.referencia(), actual.observacion(), "ANULADO");
            guardar(anulado);
            publicarRecalculo(actual.facturaId());
            return anulado;
        });
    }

    public BigDecimal calcularPendiente(FacturaDTO factura) {
        return SaldoCalculator.pendiente(factura, listarTodos(), notaCreditoService.listar());
    }

    private String normalizarMedio(String medio) {
        String normalized = medio == null || medio.isBlank()
                ? "TRANSFERENCIA"
                : medio.trim().toUpperCase(Locale.ROOT);
        if (!MEDIOS.contains(normalized)) {
            throw ApiException.badRequest("MEDIO_INVALIDO",
                    "Medio de pago invalido. Valores permitidos: " + MEDIOS);
        }
        return normalized;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private void guardar(PagoDTO pago) {
        phaseClient.writeJson(GLOBAL, pago.id(), PhaseJson.toJson(pago, objectMapper), true);
    }

    private void compensarPago(String pagoId, RuntimeException causa) {
        log.warn("Compensando pago {} tras fallo de escritura: {}", pagoId, causa.getMessage());
        try {
            phaseClient.kill(GLOBAL, pagoId);
        } catch (RuntimeException e) {
            log.error("No se pudo compensar el pago {}", pagoId, e);
        }
    }

    private void publicarRecalculo(String facturaId) {
        try {
            eventPublisher.publishEvent(new FacturaActualizadaEvent(facturaId));
        } catch (RuntimeException e) {
            log.warn("No se pudo recalcular la factura {}: {}", facturaId, e.getMessage());
        }
    }
}
