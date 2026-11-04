package com.phase.api.service;

import com.phase.api.common.ApiException;
import com.phase.api.common.SequenceService;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.dto.NotaCreditoDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import org.springframework.http.HttpStatus;
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
public class NotaCreditoService {

    public static final String GLOBAL = "NOTASCREDITO";

    private static final Set<String> TIPOS = Set.of("ANULACION", "DESCUENTO", "DEVOLUCION");

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final SequenceService sequenceService;
    private final FacturaService facturaService;
    private final ClienteService clienteService;

    public NotaCreditoService(PhaseClient phaseClient,
                              ObjectMapper objectMapper,
                              SequenceService sequenceService,
                              FacturaService facturaService,
                              ClienteService clienteService) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.sequenceService = sequenceService;
        this.facturaService = facturaService;
        this.clienteService = clienteService;
    }

    public NotaCreditoDTO crear(NotaCreditoDTO dto) {
        FacturaDTO factura = facturaService.obtenerPorId(dto.facturaId());
        if ("ANULADA".equalsIgnoreCase(factura.getEstado())) {
            throw ApiException.conflict("FACTURA_ANULADA", "La factura ya esta anulada");
        }

        String tipo = normalizarTipo(dto.tipo());
        BigDecimal subtotal = factura.getSubtotal() == null ? BigDecimal.ZERO : factura.getSubtotal();
        BigDecimal totalFactura = SaldoCalculator.total(factura);

        List<NotaCreditoDTO> notas = listarPorFactura(factura.getId());
        BigDecimal netoEmitido = sumar(notas, true);
        BigDecimal totalEmitido = sumar(notas, false);
        BigDecimal saldoNeto = subtotal.subtract(netoEmitido).max(BigDecimal.ZERO);
        BigDecimal saldoTotal = totalFactura.subtract(totalEmitido).max(BigDecimal.ZERO);

        BigDecimal montoNeto;
        BigDecimal montoTotal;

        if ("ANULACION".equals(tipo)) {
            montoNeto = saldoNeto;
            montoTotal = saldoTotal;
            if (montoNeto.signum() <= 0 && montoTotal.signum() <= 0) {
                throw ApiException.conflict("FACTURA_SIN_SALDO", "La factura ya no tiene saldo por anular");
            }
        } else {
            if (dto.montoNeto() == null || dto.montoNeto().signum() <= 0) {
                throw ApiException.badRequest("MONTO_REQUERIDO", "Debes indicar el monto neto a rebajar");
            }
            montoNeto = dto.montoNeto().setScale(2, RoundingMode.HALF_UP);
            if (montoNeto.compareTo(saldoNeto) > 0) {
                throw ApiException.unprocessable("MONTO_EXCEDE_SALDO",
                        "El monto supera el saldo neto de la factura",
                        Map.of("montoNeto", "Saldo disponible: " + saldoNeto));
            }
            BigDecimal factor = subtotal.signum() > 0
                    ? totalFactura.divide(subtotal, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;
            montoTotal = montoNeto.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            if (montoTotal.compareTo(saldoTotal) > 0) {
                montoTotal = saldoTotal;
            }
        }

        LocalDate fecha = parseFecha(dto.fechaEmision());
        NotaCreditoDTO nota = new NotaCreditoDTO(
                sequenceService.nextId("NC"),
                factura.getId(),
                factura.getRutCliente(),
                clienteService.nombreDe(factura.getRutCliente()),
                tipo,
                montoNeto.setScale(2, RoundingMode.HALF_UP),
                montoTotal.setScale(2, RoundingMode.HALF_UP),
                dto.motivo().trim(),
                fecha.toString(),
                "EMITIDA"
        );

        guardar(nota);
        if ("ANULACION".equals(tipo)) {
            facturaService.actualizarEstado(factura.getId(), "ANULADA");
        }
        return nota;
    }

    public List<NotaCreditoDTO> listar() {
        Map<String, NotaCreditoDTO> notas =
                PhaseJson.toMap(phaseClient.readAll(GLOBAL), NotaCreditoDTO.class, objectMapper);
        return new ArrayList<>(notas.values());
    }

    public List<NotaCreditoDTO> listarPorFactura(String facturaId) {
        String key = facturaId == null ? "" : facturaId.trim();
        return listar().stream()
                .filter(nota -> key.equalsIgnoreCase(nota.facturaId()))
                .sorted(Comparator.comparing(NotaCreditoDTO::fechaEmision,
                        Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
    }

    public NotaCreditoDTO obtener(String id) {
        String json = phaseClient.read(GLOBAL, id);
        NotaCreditoDTO nota = PhaseJson.toObject(json, NotaCreditoDTO.class, objectMapper);
        if (nota == null) {
            throw ApiException.notFound("Nota de credito no encontrada: " + id);
        }
        return nota;
    }

    public NotaCreditoDTO anular(String id) {
        NotaCreditoDTO nota = obtener(id);
        if (!"EMITIDA".equalsIgnoreCase(nota.estado())) {
            throw ApiException.conflict("NOTA_YA_ANULADA", "La nota de credito ya esta anulada");
        }
        NotaCreditoDTO anulada = new NotaCreditoDTO(
                nota.id(), nota.facturaId(), nota.rutCliente(), nota.cliente(), nota.tipo(),
                nota.montoNeto(), nota.montoTotal(), nota.motivo(), nota.fechaEmision(), "ANULADA");
        guardar(anulada);

        if ("ANULACION".equalsIgnoreCase(nota.tipo())) {
            boolean otraVigente = listarPorFactura(nota.facturaId()).stream()
                    .anyMatch(otra -> !otra.id().equals(id)
                            && "EMITIDA".equalsIgnoreCase(otra.estado())
                            && "ANULACION".equalsIgnoreCase(otra.tipo()));
            if (!otraVigente) {
                FacturaDTO factura = facturaService.obtenerPorId(nota.facturaId());
                if ("ANULADA".equalsIgnoreCase(factura.getEstado())) {
                    facturaService.actualizarEstado(nota.facturaId(), "PENDIENTE");
                }
            }
        }
        return anulada;
    }

    public BigDecimal totalCredito(String facturaId) {
        return sumar(listarPorFactura(facturaId), false);
    }

    private BigDecimal sumar(List<NotaCreditoDTO> notas, boolean neto) {
        return notas.stream()
                .filter(nota -> "EMITIDA".equalsIgnoreCase(nota.estado()))
                .map(nota -> {
                    BigDecimal value = neto ? nota.montoNeto() : nota.montoTotal();
                    return value == null ? BigDecimal.ZERO : value;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalizarTipo(String tipo) {
        String normalized = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
        if (!TIPOS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TIPO_INVALIDO",
                    "Tipo de nota de credito invalido. Valores permitidos: " + TIPOS);
        }
        return normalized;
    }

    private LocalDate parseFecha(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private void guardar(NotaCreditoDTO nota) {
        phaseClient.writeJson(GLOBAL, nota.id(), PhaseJson.toJson(nota, objectMapper), true);
    }
}
