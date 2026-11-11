package com.phase.api.service;

import tools.jackson.databind.ObjectMapper;
import com.phase.api.common.ApiException;
import com.phase.api.common.FechaUtil;
import com.phase.api.common.PhaseLockService;
import com.phase.api.common.SequenceService;
import com.phase.api.config.FinanceProperties;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.dto.NotaCreditoDTO;
import com.phase.api.dto.PagoDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FacturaService {

    public static final String GLOBAL = "FACTURAS";
    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "PAGADA", "VENCIDA", "ANULADA");
    private static final Set<String> CONDICIONES = Set.of("CONTADO", "NETO_30", "NETO_60");

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final FinanceProperties financeProperties;
    private final SequenceService sequenceService;
    private final PhaseLockService lockService;

    public FacturaService(PhaseClient phaseClient,
                          ObjectMapper objectMapper,
                          FinanceProperties financeProperties,
                          SequenceService sequenceService,
                          PhaseLockService lockService) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.financeProperties = financeProperties;
        this.sequenceService = sequenceService;
        this.lockService = lockService;
    }

    public FacturaDTO emitirFactura(FacturaDTO factura) {
        if (factura.getId() == null || factura.getId().isBlank()) {
            factura.setId(sequenceService.nextId("FAC"));
        }
        factura.setEstado("PENDIENTE");
        preparar(factura);
        guardar(factura);
        return factura;
    }

    public FacturaDTO actualizar(String id, FacturaDTO factura) {
        return lockService.conLock("FACTURA:" + id, () -> {
            FacturaDTO actual = obtenerPorId(id);
            if (tieneMovimientos(id)) {
                throw ApiException.conflict("FACTURA_CON_MOVIMIENTOS",
                        "No se puede editar una factura que ya tiene pagos o notas de credito vigentes");
            }

            factura.setId(id);
            if (factura.getFechaEmision() == null || factura.getFechaEmision().isBlank()) {
                factura.setFechaEmision(actual.getFechaEmision());
            }
            if (factura.getCondicionPago() == null || factura.getCondicionPago().isBlank()) {
                factura.setCondicionPago(actual.getCondicionPago());
            }
            if (factura.getIdOperacionOrigen() == null || factura.getIdOperacionOrigen().isBlank()) {
                factura.setIdOperacionOrigen(actual.getIdOperacionOrigen());
            }

            preparar(factura);
            factura.setEstado(actual.getEstado());
            guardar(factura);
            return factura;
        });
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
        lockService.conLock("FACTURA:" + id, () -> {
            FacturaDTO actual = obtenerPorId(id);
            String destino = normalizarEstado(nuevoEstado);
            validarTransicion(actual, destino);
            cambiarEstadoSistema(id, destino);
        });
    }

    /** Cambio de estado interno: no valida transiciones, lo usan pagos y notas. */
    public void cambiarEstadoSistema(String id, String nuevoEstado) {
        if (!phaseClient.exists(GLOBAL, id)) {
            throw ApiException.notFound("Factura no encontrada: " + id);
        }
        String estado = normalizarEstado(nuevoEstado);
        phaseClient.writePartialJson(GLOBAL, id, "{\"estado\":\"" + estado + "\"}");
    }

    private void validarTransicion(FacturaDTO actual, String destino) {
        String origen = normalizarEstado(actual.getEstado());
        if (origen.equals(destino)) {
            return;
        }

        if ("PAGADA".equals(destino) || "VENCIDA".equals(destino)) {
            throw ApiException.conflict("TRANSICION_INVALIDA",
                    "El estado " + destino + " lo asigna el sistema a partir de los movimientos de la factura");
        }

        if ("PENDIENTE".equals(origen) && "ANULADA".equals(destino)) {
            if (tienePagosVigentes(actual.getId())) {
                throw ApiException.conflict("TRANSICION_INVALIDA",
                        "Anula primero los pagos vigentes antes de anular la factura");
            }
            return;
        }

        if ("ANULADA".equals(origen) && "PENDIENTE".equals(destino)) {
            if (tienePagosVigentes(actual.getId())) {
                throw ApiException.conflict("TRANSICION_INVALIDA",
                        "No se puede reactivar una factura con pagos vigentes");
            }
            if (tieneNotaAnulacionVigente(actual.getId())) {
                throw ApiException.conflict("TRANSICION_INVALIDA",
                        "Anula primero la nota de credito de anulacion");
            }
            return;
        }

        throw ApiException.conflict("TRANSICION_INVALIDA",
                "Transicion no permitida: " + origen + " -> " + destino);
    }

    private void preparar(FacturaDTO factura) {
        if (factura.getFechaEmision() == null || factura.getFechaEmision().isBlank()) {
            factura.setFechaEmision(LocalDate.now().toString());
        } else {
            factura.setFechaEmision(FechaUtil.requerida(factura.getFechaEmision(), "fechaEmision").toString());
        }

        String condicion = factura.getCondicionPago() == null || factura.getCondicionPago().isBlank()
                ? "NETO_30"
                : factura.getCondicionPago().trim().toUpperCase(Locale.ROOT);
        if (!CONDICIONES.contains(condicion)) {
            throw ApiException.badRequest("CONDICION_INVALIDA",
                    "Condicion de pago invalida. Valores permitidos: " + CONDICIONES);
        }
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
        LocalDate emision = LocalDate.parse(fechaEmision);
        int days = switch (condicion) {
            case "CONTADO" -> 0;
            case "NETO_60" -> 60;
            default -> 30;
        };
        return emision.plusDays(days).toString();
    }

    private boolean tieneMovimientos(String facturaId) {
        return tienePagosVigentes(facturaId) || tieneNotasVigentes(facturaId);
    }

    private boolean tienePagosVigentes(String facturaId) {
        Map<String, PagoDTO> pagos =
                PhaseJson.toMap(phaseClient.readAll("PAGOS"), PagoDTO.class, objectMapper);
        return pagos.values().stream()
                .anyMatch(pago -> facturaId.equalsIgnoreCase(pago.facturaId())
                        && !"ANULADO".equalsIgnoreCase(pago.estado()));
    }

    private boolean tieneNotasVigentes(String facturaId) {
        return notasVigentes(facturaId, false);
    }

    private boolean tieneNotaAnulacionVigente(String facturaId) {
        return notasVigentes(facturaId, true);
    }

    private boolean notasVigentes(String facturaId, boolean soloAnulacion) {
        Map<String, NotaCreditoDTO> notas =
                PhaseJson.toMap(phaseClient.readAll("NOTASCREDITO"), NotaCreditoDTO.class, objectMapper);
        return notas.values().stream()
                .anyMatch(nota -> facturaId.equalsIgnoreCase(nota.facturaId())
                        && !"ANULADA".equalsIgnoreCase(nota.estado())
                        && (!soloAnulacion || "ANULACION".equalsIgnoreCase(nota.tipo())));
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
