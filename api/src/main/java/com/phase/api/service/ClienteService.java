package com.phase.api.service;

import tools.jackson.databind.ObjectMapper;
import com.phase.api.common.ApiException;
import com.phase.api.common.PageResponse;
import com.phase.api.common.PhaseLockService;
import com.phase.api.common.RutValidator;
import com.phase.api.config.ValidationProperties;
import com.phase.api.dto.ClienteDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ClienteService {

    public static final String GLOBAL = "CLIENTES";

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final boolean validateRutDv;
    private final PhaseLockService lockService;

    public ClienteService(PhaseClient phaseClient,
                          ObjectMapper objectMapper,
                          ValidationProperties validationProperties,
                          PhaseLockService lockService) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.validateRutDv = validationProperties.isRutDv();
        this.lockService = lockService;
    }

    public ClienteDTO crear(ClienteDTO dto) {
        String key = normalizarDocumento(dto.getNumDoc());
        return lockService.conLock("CLIENTE:" + key, () -> {
            dto.setNumDoc(key);
            dto.setTipoDoc(normalizarTipoDoc(dto.getTipoDoc()));
            dto.setPais(normalizarPais(dto.getPais()));
            dto.setActivo(true);
            validarDocumento(dto);

            if (phaseClient.exists(GLOBAL, key)) {
                throw ApiException.conflict("CLIENTE_EXISTE", "Ya existe un cliente con el documento " + key);
            }

            guardar(dto);
            return dto;
        });
    }

    public ClienteDTO actualizar(String numDoc, ClienteDTO dto) {
        String key = normalizarDocumento(numDoc);
        return lockService.conLock("CLIENTE:" + key, () -> {
            if (!phaseClient.exists(GLOBAL, key)) {
                throw ApiException.notFound("Cliente no encontrado: " + numDoc);
            }

            String bodyKey = normalizarDocumento(dto.getNumDoc());
            if (bodyKey != null && !bodyKey.equals(key)) {
                throw ApiException.badRequest("DOCUMENTO_INMUTABLE",
                        "El documento del cliente no se puede cambiar en esta operacion");
            }

            dto.setNumDoc(key);
            dto.setTipoDoc(normalizarTipoDoc(dto.getTipoDoc()));
            dto.setPais(normalizarPais(dto.getPais()));
            validarDocumento(dto);
            guardar(dto);
            return dto;
        });
    }

    public ClienteDTO obtener(String numDoc) {
        String key = normalizarDocumento(numDoc);
        String json = phaseClient.read(GLOBAL, key);
        if (json == null) {
            throw ApiException.notFound("Cliente no encontrado: " + numDoc);
        }
        ClienteDTO dto = PhaseJson.toObject(json, ClienteDTO.class, objectMapper);
        if (dto == null) {
            throw ApiException.notFound("Cliente no encontrado: " + numDoc);
        }
        normalizarLegacy(dto, key);
        return dto;
    }

    public PageResponse<ClienteDTO> listar(String q, Boolean activo, int page, int size) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<ClienteDTO> filtrados = new ArrayList<>();

        for (ClienteDTO cliente : listarTodos()) {
            if (activo != null && cliente.isActivo() != activo) {
                continue;
            }
            if (!query.isEmpty() && !coincide(cliente, query)) {
                continue;
            }
            filtrados.add(cliente);
        }

        filtrados.sort(Comparator.comparing(ClienteDTO::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)));
        return PageResponse.of(filtrados, page, size);
    }

    public List<ClienteDTO> buscarPorNombre(String q) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return listarTodos().stream()
                .filter(cliente -> coincide(cliente, query))
                .toList();
    }

    public void desactivar(String numDoc) {
        cambiarActivo(numDoc, false);
    }

    public void activar(String numDoc) {
        cambiarActivo(numDoc, true);
    }

    public void cambiarActivo(String numDoc, boolean activo) {
        String key = normalizarDocumento(numDoc);
        lockService.conLock("CLIENTE:" + key, () -> {
            if (!phaseClient.exists(GLOBAL, key)) {
                throw ApiException.notFound("Cliente no encontrado: " + numDoc);
            }
            phaseClient.writePartialJson(GLOBAL, key, "{\"activo\":" + activo + "}");
        });
    }

    public List<ClienteDTO> listarTodos() {
        return new ArrayList<>(listarTodosRaw().values());
    }

    /** Nombre del cliente para denormalizar en pagos, notas y cartera. */
    public String nombreDe(String numDoc) {
        String key = normalizarDocumento(numDoc);
        if (key == null || key.isBlank()) {
            return null;
        }
        String json = phaseClient.read(GLOBAL, key);
        ClienteDTO cliente = PhaseJson.toObject(json, ClienteDTO.class, objectMapper);
        return cliente == null ? null : cliente.getNombre();
    }

    public Map<String, String> nombresPorDocumento() {
        Map<String, String> nombres = new java.util.LinkedHashMap<>();
        listarTodosRaw().forEach((key, cliente) -> nombres.put(key, cliente.getNombre()));
        return nombres;
    }

    public Map<String, ClienteDTO> listarTodosRaw() {
        String json = phaseClient.readAll(GLOBAL);
        Map<String, ClienteDTO> raw = PhaseJson.toMap(json, ClienteDTO.class, objectMapper);
        raw.forEach((key, dto) -> normalizarLegacy(dto, key));
        return raw;
    }

    /**
     * Usado por la migracion para reescribir registros legacy sin volver a
     * validar documentos historicos.
     */
    public void migrar(String key, ClienteDTO dto) {
        String normalizedKey = normalizarDocumento(key);
        normalizarLegacy(dto, normalizedKey);
        guardar(dto);
    }

    private void guardar(ClienteDTO dto) {
        String json = PhaseJson.toJson(dto, objectMapper);
        phaseClient.writeJson(GLOBAL, dto.getNumDoc(), json, true);
    }

    private void normalizarLegacy(ClienteDTO dto, String key) {
        if (dto.getNumDoc() == null || dto.getNumDoc().isBlank()) {
            dto.setNumDoc(key);
        } else {
            dto.setNumDoc(normalizarDocumento(dto.getNumDoc()));
        }
        if (dto.getTipoDoc() == null || dto.getTipoDoc().isBlank()) {
            dto.setTipoDoc("RUT");
        } else {
            dto.setTipoDoc(normalizarTipoDoc(dto.getTipoDoc()));
        }
        if (dto.getPais() == null || dto.getPais().isBlank()) {
            dto.setPais("cl");
        } else {
            dto.setPais(normalizarPais(dto.getPais()));
        }
        if (dto.getRegion() == null || dto.getRegion().isBlank()) {
            dto.setRegion(inferirRegion(dto.getDireccion()));
        }
        if (dto.getGlosa() == null) {
            dto.setGlosa("");
        }
    }

    private void validarDocumento(ClienteDTO dto) {
        String tipo = normalizarTipoDoc(dto.getTipoDoc());
        String documento = normalizarDocumento(dto.getNumDoc());

        if ("RUT".equals(tipo)) {
            boolean valid = validateRutDv
                    ? RutValidator.isValidRut(documento)
                    : RutValidator.isValidRutFormat(documento);
            if (!valid) {
                throw ApiException.unprocessable(
                        "RUT_INVALIDO",
                        "El RUT no es valido",
                        Map.of("numDoc", validateRutDv
                                ? "Formato o digito verificador invalido"
                                : "Usa el formato 12345678-9")
                );
            }
        } else if (!RutValidator.isValidForeignDocument(documento)) {
            throw ApiException.unprocessable(
                    "DOCUMENTO_INVALIDO",
                    "El documento no es valido",
                    Map.of("numDoc", "Usa entre 3 y 20 caracteres alfanumericos")
            );
        }
    }

    private String normalizarDocumento(String value) {
        return RutValidator.normalize(value);
    }

    private String normalizarTipoDoc(String value) {
        if (value == null || value.isBlank()) {
            return "RUT";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizarPais(String value) {
        if (value == null || value.isBlank()) {
            return "cl";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String inferirRegion(String direccion) {
        String value = direccion == null ? "" : direccion.toLowerCase(Locale.ROOT);
        if (value.contains("valparaiso") || value.contains("valparaíso")) {
            return "v";
        }
        if (value.contains("santiago")
                || value.contains("quilicura")
                || value.contains("providencia")
                || value.contains("las condes")
                || value.contains("maipu")
                || value.contains("maipú")) {
            return "rm";
        }
        return "no-especificada";
    }

    private boolean coincide(ClienteDTO cliente, String query) {
        return contiene(cliente.getNombre(), query)
                || contiene(cliente.getNumDoc(), query)
                || contiene(cliente.getEmail(), query)
                || contiene(cliente.getGlosa(), query);
    }

    private boolean contiene(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
