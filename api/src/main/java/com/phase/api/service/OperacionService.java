package com.phase.api.service;

import tools.jackson.databind.ObjectMapper;
import com.phase.api.common.ApiException;
import com.phase.api.common.SequenceService;
import com.phase.api.dto.OperacionDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OperacionService {

    public static final String GLOBAL = "OPERACIONES";
    private static final Set<String> ESTADOS =
            Set.of("PLANIFICADA", "EN_CURSO", "FINALIZADA", "CANCELADA", "ATRASADO");

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final SequenceService sequenceService;

    public OperacionService(PhaseClient phaseClient,
                            ObjectMapper objectMapper,
                            SequenceService sequenceService) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.sequenceService = sequenceService;
    }

    public OperacionDTO crearOperacion(OperacionDTO operacion) {
        if (operacion.getId() == null || operacion.getId().isBlank()) {
            operacion.setId(sequenceService.nextId("OP"));
        }
        preparar(operacion);
        guardar(operacion);
        return operacion;
    }

    public OperacionDTO actualizar(String id, OperacionDTO operacion) {
        if (!phaseClient.exists(GLOBAL, id)) {
            throw ApiException.notFound("Operacion no encontrada: " + id);
        }
        operacion.setId(id);
        preparar(operacion);
        guardar(operacion);
        return operacion;
    }

    public List<OperacionDTO> listarTodas() {
        String json = phaseClient.readAll(GLOBAL);
        Map<String, OperacionDTO> operaciones = PhaseJson.toMap(json, OperacionDTO.class, objectMapper);
        return new ArrayList<>(operaciones.values());
    }

    public OperacionDTO obtenerPorId(String id) {
        String json = phaseClient.read(GLOBAL, id);
        OperacionDTO operacion = PhaseJson.toObject(json, OperacionDTO.class, objectMapper);
        if (operacion == null) {
            throw ApiException.notFound("Operacion no encontrada: " + id);
        }
        return operacion;
    }

    public void actualizarEstado(String id, String nuevoEstado) {
        if (!phaseClient.exists(GLOBAL, id)) {
            throw ApiException.notFound("Operacion no encontrada: " + id);
        }
        String estado = normalizarEstado(nuevoEstado);
        phaseClient.writePartialJson(GLOBAL, id, "{\"estado\":\"" + estado + "\"}");
    }

    public OperacionDTO agregarDocumento(String id, OperacionDTO.DocumentoOperacionDTO documento) {
        OperacionDTO operacion = obtenerPorId(id);
        if (operacion.getDocumentos() == null) {
            operacion.setDocumentos(new ArrayList<>());
        }
        operacion.getDocumentos().add(documento);
        guardar(operacion);
        return operacion;
    }

    private void preparar(OperacionDTO operacion) {
        if (operacion.getFechaInicio() == null || operacion.getFechaInicio().isBlank()) {
            operacion.setFechaInicio(LocalDate.now().toString());
        }
        if (operacion.getTipoOperacion() != null) {
            operacion.setTipoOperacion(operacion.getTipoOperacion().trim().toUpperCase(Locale.ROOT));
        }
        if (operacion.getViaTransporte() != null) {
            operacion.setViaTransporte(operacion.getViaTransporte().trim().toUpperCase(Locale.ROOT));
        }
        if (operacion.getDocumentos() == null) {
            operacion.setDocumentos(new ArrayList<>());
        }
        if (operacion.getEstado() == null || operacion.getEstado().isBlank()) {
            operacion.setEstado("PLANIFICADA");
        } else {
            operacion.setEstado(normalizarEstado(operacion.getEstado()));
        }
    }

    private String normalizarEstado(String estado) {
        String normalized = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(normalized)) {
            throw ApiException.badRequest("ESTADO_INVALIDO",
                    "Estado de operacion invalido. Valores permitidos: " + ESTADOS);
        }
        return normalized;
    }

    private void guardar(OperacionDTO operacion) {
        String json = PhaseJson.toJson(operacion, objectMapper);
        phaseClient.writeJson(GLOBAL, operacion.getId(), json, true);
    }
}
