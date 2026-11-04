package com.phase.api.service;

import tools.jackson.databind.ObjectMapper;
import com.phase.api.common.ApiException;
import com.phase.api.common.SequenceService;
import com.phase.api.config.FinanceProperties;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FacturaService {

    public static final String GLOBAL = "FACTURAS";
    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "PAGADA", "VENCIDA", "ANULADA");

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final FinanceProperties financeProperties;
    private final SequenceService sequenceService;

    public FacturaService(PhaseClient phaseClient,
                          ObjectMapper objectMapper,
                          FinanceProperties financeProperties,
                          SequenceService sequenceService) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.financeProperties = financeProperties;
        this.sequenceService = sequenceService;
    }

    public FacturaDTO emitirFactura(FacturaDTO factura) {
        if (factura.getId() == null || factura.getId().isBlank()) {
            factura.setId(sequenceService.nextId("FAC"));
        }
        preparar(factura);
        guardar(factura);
        return factura;
    }

    public FacturaDTO actualizar(String id, FacturaDTO factura) {
        if (!phaseClient.exists(GLOBAL, id)) {
            throw ApiException.notFound("Factura no encontrada: " + id);
        }
        factura.setId(id);
        preparar(factura);
        guardar(factura);
        return factura;
    }

    public List<FacturaDTO> listarTodas() {
        String json = phaseClient.readAll(GLOBAL);
        Map<String, FacturaDTO> facturas = PhaseJson.toMap(json, FacturaDTO.class, objectMapper);
        return new ArrayList<>(facturas.values());
    }

    public FacturaDTO obtenerPorId(String id) {
        String json = phaseClient.read(GLOBAL, id);
        FacturaDTO factura = PhaseJson.toObject(json, FacturaDTO.class, objectMapper);
        if (factura == null) {
            throw ApiException.notFound("Factura no encontrada: " + id);
        }
        return factura;
    }

    public void actualizarEstado(String id, String nuevoEstado) {
        if (!phaseClient.exists(GLOBAL, id)) {
            throw ApiException.notFound("Factura no encontrada: " + id);
        }
        String estado = normalizarEstado(nuevoEstado);
        phaseClient.writePartialJson(GLOBAL, id, "{\"estado\":\"" + estado + "\"}");
    }

    private void preparar(FacturaDTO factura) {
        if (factura.getFechaEmision() == null || factura.getFechaEmision().isBlank()) {
            factura.setFechaEmision(LocalDate.now().toString());
        }

        String condicion = factura.getCondicionPago() == null || factura.getCondicionPago().isBlank()
                ? "NETO_30"
                : factura.getCondicionPago().trim().toUpperCase(Locale.ROOT);
        factura.setCondicionPago(condicion);
        factura.setFechaVencimiento(calcularVencimiento(factura.getFechaEmision(), condicion));

        BigDecimal subtotal = BigDecimal.ZERO;
        for (FacturaDTO.ItemFacturaDTO item : factura.getItems()) {
            BigDecimal precio = item.getPrecioUnitario() == null ? BigDecimal.ZERO : item.getPrecioUnitario();
            BigDecimal totalLinea = precio.multiply(BigDecimal.valueOf(item.getCantidad()))
                    .setScale(2, RoundingMode.HALF_UP);
            item.setPrecioUnitario(precio.setScale(2, RoundingMode.HALF_UP));
            item.setTotalLinea(totalLinea);
            subtotal = subtotal.add(totalLinea);
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal impuestos = subtotal.multiply(financeProperties.getIvaRate())
                .setScale(2, RoundingMode.HALF_UP);
        factura.setSubtotal(subtotal);
        factura.setImpuestos(impuestos);
        factura.setTotal(subtotal.add(impuestos).setScale(2, RoundingMode.HALF_UP));

        if (factura.getEstado() == null || factura.getEstado().isBlank()) {
            factura.setEstado("PENDIENTE");
        } else {
            factura.setEstado(normalizarEstado(factura.getEstado()));
        }
    }

    private String calcularVencimiento(String fechaEmision, String condicion) {
        LocalDate emision = parseDate(fechaEmision);
        int days = switch (condicion) {
            case "CONTADO" -> 0;
            case "NETO_60" -> 60;
            default -> 30;
        };
        return emision.plusDays(days).toString();
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }

    private String normalizarEstado(String estado) {
        String normalized = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(normalized)) {
            throw ApiException.badRequest("ESTADO_INVALIDO",
                    "Estado de factura invalido. Valores permitidos: " + ESTADOS);
        }
        return normalized;
    }

    private void guardar(FacturaDTO factura) {
        String json = PhaseJson.toJson(factura, objectMapper);
        phaseClient.writeJson(GLOBAL, factura.getId(), json, true);
    }
}
