package com.phase.api.service;

import tools.jackson.databind.ObjectMapper;
import com.phase.api.common.ApiException;
import com.phase.api.common.SequenceService;
import com.phase.api.dto.OperacionDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final ArchivoOperacionService archivoOperacionService;

    public OperacionService(PhaseClient phaseClient,
                            ObjectMapper objectMapper,
                            SequenceService sequenceService,
                            ArchivoOperacionService archivoOperacionService) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.sequenceService = sequenceService;
        this.archivoOperacionService = archivoOperacionService;
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

    public OperacionDTO agregarDocumentoConArchivo(String id,
                                                   String tipo,
                                                   String numero,
                                                   String fecha,
                                                   MultipartFile archivo) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (tipo == null || tipo.isBlank()) {
            errores.put("tipo", "El tipo de documento es obligatorio");
        }
        if (numero == null || numero.isBlank()) {
            errores.put("numero", "El numero de documento es obligatorio");
        }
        if (archivo == null || archivo.isEmpty()) {
            errores.put("archivo", "Selecciona un archivo");
        }
        if (!errores.isEmpty()) {
            throw ApiException.unprocessable(
                    "VALIDACION",
                    "Los datos enviados no son validos",
                    errores
            );
        }

        OperacionDTO operacion = obtenerPorId(id);
        ArchivoOperacionService.ArchivoGuardado guardado = archivoOperacionService.guardar(id, archivo);

        OperacionDTO.DocumentoOperacionDTO documento = new OperacionDTO.DocumentoOperacionDTO();
        documento.setTipo(tipo.trim());
        documento.setNumero(numero.trim());
        documento.setFecha(fecha == null || fecha.isBlank() ? null : fecha.trim());
        documento.setArchivo(guardado.nombre());
        documento.setArchivoToken(guardado.token());

        if (operacion.getDocumentos() == null) {
            operacion.setDocumentos(new ArrayList<>());
        }
        operacion.getDocumentos().add(documento);
        try {
            guardar(operacion);
        } catch (RuntimeException ex) {
            archivoOperacionService.eliminar(id, guardado.token());
            throw ex;
        }
        return operacion;
    }

    public DocumentoArchivo obtenerArchivo(String id, String token) {
        OperacionDTO.DocumentoOperacionDTO documento = buscarDocumentoPorToken(id, token);
        Resource recurso = archivoOperacionService.cargar(id, token);
        String nombre = documento.getArchivo();
        if (nombre == null || nombre.isBlank()) {
            nombre = "documento";
        }
        return new DocumentoArchivo(recurso, nombre, archivoOperacionService.contentType(id, token));
    }

    private OperacionDTO.DocumentoOperacionDTO buscarDocumentoPorToken(String id, String token) {
        OperacionDTO operacion = obtenerPorId(id);
        if (operacion.getDocumentos() == null || token == null) {
            throw ApiException.notFound("Archivo no encontrado");
        }
        return operacion.getDocumentos().stream()
                .filter(documento -> token.equals(documento.getArchivoToken()))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Archivo no encontrado"));
    }

    private void preparar(OperacionDTO operacion) {
        if (operacion.getFechaInicio() == null || operacion.getFechaInicio().isBlank()) {
            operacion.setFechaInicio(LocalDate.now().toString());
        } else {
            operacion.setFechaInicio(
                    com.phase.api.common.FechaUtil.requerida(operacion.getFechaInicio(), "fechaInicio").toString());
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

    public record DocumentoArchivo(Resource recurso, String nombre, MediaType contentType) {
    }
}
