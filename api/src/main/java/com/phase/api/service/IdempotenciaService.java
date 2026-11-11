package com.phase.api.service;

import com.phase.api.common.ApiException;
import com.phase.api.phase.PhaseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Idempotencia para escrituras financieras.
 *
 * <p>Dos capas:</p>
 * <ol>
 *   <li><b>Observador en memoria:</b> si dos requests con la misma clave llegan
 *       a la vez, el segundo espera sobre el future del primero y recibe la
 *       misma respuesta.</li>
 *   <li><b>Registro en PhaseDB ({@code ^IDEMPOTENCIA}):</b> si la clave ya se
 *       completo, se devuelve la respuesta guardada; si el cuerpo cambio, se
 *       responde {@code 409}.</li>
 * </ol>
 */
@Service
public class IdempotenciaService {

    private static final Logger log = LoggerFactory.getLogger(IdempotenciaService.class);
    private static final String GLOBAL = "IDEMPOTENCIA";
    private static final long ESPERA_MS = 200;
    private static final long ABANDONO_MS = 60_000;

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final long esperaMs;
    private final long ttlLargoSegundos;
    private final long ttlCortoSegundos;

    private final ConcurrentHashMap<String, CompletableFuture<Object>> enCurso = new ConcurrentHashMap<>();

    public IdempotenciaService(PhaseClient phaseClient,
                               ObjectMapper objectMapper,
                               @Value("${erp.idempotencia.espera-ms:10000}") long esperaMs,
                               @Value("${erp.idempotencia.ttl-seconds:86400}") long ttlLargoSegundos,
                               @Value("${erp.idempotencia.ventana-corta-seconds:30}") long ttlCortoSegundos) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.esperaMs = esperaMs;
        this.ttlLargoSegundos = ttlLargoSegundos;
        this.ttlCortoSegundos = ttlCortoSegundos;
    }

    public record Resultado<T>(T valor, boolean replay) {
    }

    public record Clave(String valor, long ttlSegundos) {
    }

    /**
     * Construye la clave de idempotencia.
     *
     * @param header clave enviada por el cliente en {@code Idempotency-Key}
     * @param prefijo prefijo del endpoint
     * @param partes datos de negocio usados para derivar la clave
     */
    public Clave resolver(String header, String prefijo, Object... partes) {
        if (header != null && !header.isBlank()) {
            String limpio = header.trim();
            if (!limpio.matches("[A-Za-z0-9._:-]{1,120}")) {
                throw ApiException.badRequest("IDEMPOTENCIA_INVALIDA",
                        "Idempotency-Key solo admite letras, numeros, punto, guion, dos puntos y guion bajo (maximo 120)");
            }
            return new Clave(prefijo + ":" + limpio, ttlLargoSegundos);
        }

        StringBuilder base = new StringBuilder(prefijo);
        boolean completa = true;
        for (Object parte : partes) {
            String valor = parte == null ? "" : String.valueOf(parte).trim();
            if (valor.isEmpty()) {
                completa = false;
                continue;
            }
            base.append('|').append(valor);
        }

        if (base.length() == prefijo.length()) {
            return null;
        }

        String clave = prefijo + ":auto:" + sha256(base.toString());
        return new Clave(clave, completa ? ttlLargoSegundos : ttlCortoSegundos);
    }

    public String hash(Object request) {
        try {
            return sha256(objectMapper.writeValueAsString(request));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCIA_HASH",
                    "No se pudo calcular la huella de la peticion");
        }
    }

    public <T> Resultado<T> ejecutar(String clave, String hash, Class<T> tipo,
                                     long ttlSegundos, Supplier<T> accion) {
        if (clave == null || clave.isBlank()) {
            return new Resultado<>(accion.get(), false);
        }

        JsonNode registro = leer(clave);
        if (vigente(registro)) {
            return resolverRegistro(clave, hash, tipo, registro);
        }

        CompletableFuture<Object> propia = new CompletableFuture<>();
        CompletableFuture<Object> previa = enCurso.putIfAbsent(clave, propia);
        if (previa != null) {
            return esperarFuturo(previa, tipo);
        }

        try {
            guardar(clave, hash, "EN_PROCESO", null, ttlSegundos);
            T valor = accion.get();
            guardar(clave, hash, "COMPLETADO", valor, ttlSegundos);
            propia.complete(valor);
            return new Resultado<>(valor, false);
        } catch (RuntimeException e) {
            limpiar(clave);
            propia.completeExceptionally(e);
            throw e;
        } finally {
            enCurso.remove(clave, propia);
        }
    }

    private <T> Resultado<T> resolverRegistro(String clave, String hash, Class<T> tipo, JsonNode registro) {
        if (!hash.equals(registro.path("hash").asText())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCIA_CONFLICTO",
                    "La clave de idempotencia ya fue usada con un cuerpo distinto");
        }

        if ("COMPLETADO".equals(registro.path("estado").asText())) {
            return new Resultado<>(deserializar(registro.get("respuesta"), tipo), true);
        }

        CompletableFuture<Object> futuro = enCurso.get(clave);
        if (futuro != null) {
            return esperarFuturo(futuro, tipo);
        }
        return esperarRegistro(clave, hash, tipo);
    }

    private <T> Resultado<T> esperarRegistro(String clave, String hash, Class<T> tipo) {
        long limite = System.currentTimeMillis() + esperaMs;
        while (System.currentTimeMillis() < limite) {
            dormir();
            JsonNode actual = leer(clave);
            if (!vigente(actual)) {
                break;
            }
            if (hash.equals(actual.path("hash").asText())
                    && "COMPLETADO".equals(actual.path("estado").asText())) {
                return new Resultado<>(deserializar(actual.get("respuesta"), tipo), true);
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "OPERACION_EN_CURSO",
                "La operacion con esta clave sigue en curso. Intenta nuevamente.");
    }

    @SuppressWarnings("unchecked")
    private <T> Resultado<T> esperarFuturo(CompletableFuture<Object> futuro, Class<T> tipo) {
        try {
            Object valor = futuro.get(esperaMs, TimeUnit.MILLISECONDS);
            return new Resultado<>((T) valor, true);
        } catch (TimeoutException e) {
            throw new ApiException(HttpStatus.CONFLICT, "OPERACION_EN_CURSO",
                    "La operacion con esta clave sigue en curso. Intenta nuevamente.");
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCIA_ERROR",
                    "La operacion original fallo");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OPERACION_INTERRUMPIDA",
                    "La operacion fue interrumpida. Intenta nuevamente.");
        }
    }

    private boolean vigente(JsonNode registro) {
        if (registro == null || registro.isNull()) {
            return false;
        }
        long expira = registro.path("expira").asLong(0);
        if (expira > 0 && Instant.now().getEpochSecond() > expira) {
            return false;
        }
        if ("EN_PROCESO".equals(registro.path("estado").asText())) {
            long creado = registro.path("creado").asLong(0);
            long transcurrido = Instant.now().getEpochSecond() - creado;
            return transcurrido * 1000L < ABANDONO_MS;
        }
        return true;
    }

    private JsonNode leer(String clave) {
        String json = phaseClient.read(GLOBAL, clave);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException e) {
            log.warn("Registro de idempotencia ilegible clave={}: {}", clave, e.getMessage());
            return null;
        }
    }

    private void guardar(String clave, String hash, String estado, Object respuesta, long ttlSegundos) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clave", clave);
        data.put("hash", hash);
        data.put("estado", estado);
        data.put("creado", Instant.now().getEpochSecond());
        data.put("expira", Instant.now().getEpochSecond() + ttlSegundos);
        if (respuesta != null) {
            data.put("respuesta", respuesta);
        }
        try {
            phaseClient.writeJson(GLOBAL, clave, objectMapper.writeValueAsString(data), true);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar idempotencia clave={}: {}", clave, e.getMessage());
        }
    }

    private void limpiar(String clave) {
        try {
            phaseClient.kill(GLOBAL, clave);
        } catch (RuntimeException e) {
            log.warn("No se pudo limpiar idempotencia clave={}: {}", clave, e.getMessage());
        }
    }

    private <T> T deserializar(JsonNode nodo, Class<T> tipo) {
        if (nodo == null || nodo.isNull()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCIA_ERROR",
                    "La respuesta guardada no se puede reconstruir");
        }
        try {
            return objectMapper.treeToValue(nodo, tipo);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCIA_ERROR",
                    "La respuesta guardada no se puede reconstruir");
        }
    }

    private void dormir() {
        try {
            Thread.sleep(ESPERA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OPERACION_INTERRUMPIDA",
                    "La operacion fue interrumpida. Intenta nuevamente.");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
