package com.phase.api.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.phase.api.common.ApiException;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseException;
import com.phase.api.phase.PhaseJson;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AuditoriaService {

    private static final Map<String, String> GLOBALES = Map.ofEntries(
            Map.entry("CLIENTES", "CLIENTES"),
            Map.entry("FACTURAS", "FACTURAS"),
            Map.entry("PAGOS", "PAGOS"),
            Map.entry("NOTASCREDITO", "NOTASCREDITO"),
            Map.entry("NOTAS_CREDITO", "NOTASCREDITO"),
            Map.entry("PRESUPUESTOS", "PRESUPUESTOS"),
            Map.entry("OPERACIONES", "OPERACIONES"),
            Map.entry("CONCILIACION", "CONCILIACION"),
            Map.entry("COBRANZA", "COBRANZA"),
            Map.entry("RECORDATORIOS", "COBRANZA"),
            Map.entry("PROMESAS", "PROMESAS"),
            Map.entry("ENVIOS", "ENVIOS")
    );

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;

    public AuditoriaService(PhaseClient phaseClient, ObjectMapper objectMapper) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
    }

    public String transaccionActual() {
        return phaseClient.currentTransaction();
    }

    public Historial historial(String entidad, String clave, Long antesDe, int limite) {
        String global = resolverGlobal(entidad);
        String key = validarClave(clave);
        if (limite < 1 || limite > 100) {
            throw ApiException.badRequest("LIMITE_INVALIDO", "El limite debe estar entre 1 y 100");
        }

        String currentText = phaseClient.currentTransaction();
        long current = parsearTransaccionActual(currentText);
        long cursor = antesDe == null ? current : antesDe;
        if (cursor < 0 || cursor > current) {
            throw ApiException.badRequest(
                    "TRANSACCION_INVALIDA",
                    "El punto de partida debe estar entre cero y la transaccion actual"
            );
        }

        List<Version> versiones = new ArrayList<>();
        while (versiones.size() < limite && cursor > 0) {
            long changedAt = phaseClient.latestChanged(global, key, cursor);
            if (changedAt == 0) {
                break;
            }
            String timestamp = phaseClient.transactionTimestamp(changedAt);
            JsonNode valor = parsear(phaseClient.readAt(global, key, changedAt));

            // Si el valor es null pero la version mas reciente (inmediatamente posterior)
            // tiene el mismo timestamp, fue un KILL transitorio dentro de writeJson(replace=true).
            if (valor == null && !versiones.isEmpty()) {
                Version newer = versiones.get(versiones.size() - 1);
                if (newer.valor() != null && timestamp != null && timestamp.equals(newer.fecha())) {
                    cursor = changedAt - 1;
                    continue;
                }
            }

            versiones.add(new Version(
                    changedAt,
                    timestamp,
                    valor
            ));
            cursor = changedAt - 1;
        }

        Long siguienteAntesDe = cursor > 0 && phaseClient.latestChanged(global, key, cursor) > 0
                ? cursor
                : null;

        // Al alcanzar el final del historial inmutable, retornamos un valor conocido al final
        // (cadena terminada en null) para identificar el estado pre-creacion.
        if (siguienteAntesDe == null && !versiones.isEmpty()) {
            Version oldest = versiones.get(versiones.size() - 1);
            if (oldest.valor() != null) {
                versiones.add(new Version(0L, null, null));
            } else if (oldest.transaccion() != 0L) {
                versiones.set(versiones.size() - 1, new Version(0L, null, null));
            }
        }
        return new Historial(
                entidad.toUpperCase(Locale.ROOT),
                key,
                currentText,
                versiones,
                siguienteAntesDe
        );
    }

    private long parsearTransaccionActual(String transaccionActual) {
        try {
            return Long.parseLong(transaccionActual);
        } catch (NumberFormatException e) {
            throw new PhaseException("TX_INVALIDA", "PhaseDB devolvio un numero de transaccion invalido", null, e);
        }
    }

    private String resolverGlobal(String entidad) {
        if (entidad == null) {
            throw ApiException.badRequest("ENTIDAD_INVALIDA", "Debes seleccionar una entidad permitida");
        }
        String global = GLOBALES.get(entidad.trim().toUpperCase(Locale.ROOT));
        if (global == null) {
            throw ApiException.badRequest("ENTIDAD_INVALIDA", "La entidad no esta habilitada para auditoria");
        }
        return global;
    }

    private String validarClave(String clave) {
        if (clave == null || clave.isBlank() || clave.length() > 120) {
            throw ApiException.badRequest("CLAVE_INVALIDA", "La clave debe tener entre 1 y 120 caracteres");
        }
        return clave.trim();
    }

    private JsonNode parsear(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return PhaseJson.normalize(objectMapper.readTree(json));
        } catch (JacksonException e) {
            throw new PhaseException(
                    "JSON_PARSE",
                    "No se pudo leer el valor de auditoria: " + e.getMessage(),
                    null,
                    e
            );
        }
    }

    public record Version(long transaccion, String fecha, JsonNode valor) {
    }

    public record Historial(
            String entidad,
            String clave,
            String transaccionActual,
            List<Version> versiones,
            Long siguienteAntesDe
    ) {
    }
}
