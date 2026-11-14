package com.phase.api.service;

import tools.jackson.databind.ObjectMapper;
import com.phase.api.common.ApiException;
import com.phase.api.common.SequenceService;
import com.phase.api.config.FinanceProperties;
import com.phase.api.dto.PresupuestoDTO;
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
public class PresupuestoService {

    public static final String GLOBAL = "PRESUPUESTOS";
    private static final Set<String> ESTADOS = Set.of("BORRADOR", "ENVIADO", "APROBADO", "RECHAZADO");

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final FinanceProperties financeProperties;
    private final SequenceService sequenceService;

    public PresupuestoService(PhaseClient phaseClient,
                              ObjectMapper objectMapper,
                              FinanceProperties financeProperties,
                              SequenceService sequenceService) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.financeProperties = financeProperties;
        this.sequenceService = sequenceService;
    }

    public PresupuestoDTO crearPresupuesto(PresupuestoDTO presupuesto) {
        if (presupuesto.getId() == null || presupuesto.getId().isBlank()) {
            presupuesto.setId(sequenceService.nextId("PRE"));
        }
        preparar(presupuesto);
        guardar(presupuesto);
        return presupuesto;
    }

    public PresupuestoDTO actualizar(String id, PresupuestoDTO presupuesto) {
        if (!phaseClient.exists(GLOBAL, id)) {
            throw ApiException.notFound("Presupuesto no encontrado: " + id);
        }
        presupuesto.setId(id);
        preparar(presupuesto);
        guardar(presupuesto);
        return presupuesto;
    }

    public List<PresupuestoDTO> listarTodos() {
        String json = phaseClient.readAll(GLOBAL);
        Map<String, PresupuestoDTO> presupuestos = PhaseJson.toMap(json, PresupuestoDTO.class, objectMapper);
        return new ArrayList<>(presupuestos.values());
    }

    public PresupuestoDTO obtenerPorId(String id) {
        String json = phaseClient.read(GLOBAL, id);
        PresupuestoDTO presupuesto = PhaseJson.toObject(json, PresupuestoDTO.class, objectMapper);
        if (presupuesto == null) {
            throw ApiException.notFound("Presupuesto no encontrado: " + id);
        }
        return presupuesto;
    }

    public void actualizarEstado(String id, String nuevoEstado) {
        if (!phaseClient.exists(GLOBAL, id)) {
            throw ApiException.notFound("Presupuesto no encontrado: " + id);
        }
        String estado = normalizarEstado(nuevoEstado);
        phaseClient.writePartialJson(GLOBAL, id, "{\"estado\":\"" + estado + "\"}");
    }

    private void preparar(PresupuestoDTO presupuesto) {
        if (presupuesto.getFechaEmision() == null || presupuesto.getFechaEmision().isBlank()) {
            presupuesto.setFechaEmision(LocalDate.now().toString());
        } else {
            presupuesto.setFechaEmision(
                    com.phase.api.common.FechaUtil.requerida(presupuesto.getFechaEmision(), "fechaEmision").toString());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (PresupuestoDTO.ItemPresupuestoDTO item : presupuesto.getItems()) {
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
        presupuesto.setSubtotal(subtotal);
        presupuesto.setImpuestos(impuestos);
        presupuesto.setTotal(subtotal.add(impuestos).setScale(2, RoundingMode.HALF_UP));

        if (presupuesto.getEstado() == null || presupuesto.getEstado().isBlank()) {
            presupuesto.setEstado("BORRADOR");
        } else {
            presupuesto.setEstado(normalizarEstado(presupuesto.getEstado()));
        }
    }

    private String normalizarEstado(String estado) {
        String normalized = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(normalized)) {
            throw ApiException.badRequest("ESTADO_INVALIDO",
                    "Estado de presupuesto invalido. Valores permitidos: " + ESTADOS);
        }
        return normalized;
    }

    private void guardar(PresupuestoDTO presupuesto) {
        String json = PhaseJson.toJson(presupuesto, objectMapper);
        phaseClient.writeJson(GLOBAL, presupuesto.getId(), json, true);
    }
}
