package com.phase.api.phase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.phase.api.common.RequestIdFilter;
import com.phase.api.config.PhaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class HttpPhaseClient implements PhaseClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPhaseClient.class);
    private static final String UNDEFINED_VARIABLE = "UNDEFINED VARIABLE";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpPhaseClient(RestClient.Builder builder, PhaseProperties properties, ObjectMapper objectMapper) {
        if (isUnresolved(properties.getUsername()) || isUnresolved(properties.getPassword())) {
            throw new IllegalStateException(
                    "erp.phase.username y erp.phase.password son obligatorios (define ERP_PHASE_USER y ERP_PHASE_PASSWORD en produccion)");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());

        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.getUsername(), properties.getPassword()))
                .build();
        this.objectMapper = objectMapper;
    }

    private static boolean isUnresolved(String value) {
        return value == null || value.isBlank() || value.contains("${");
    }

    @Override
    public PhaseResponse executeRaw(String command) {
        String requestId = RequestIdFilter.currentRequestId();
        String body;
        try {
            body = restClient.post()
                    .uri("/v1/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (requestId != null) {
                            headers.set(RequestIdFilter.HEADER, requestId);
                        }
                    })
                    .body(Map.of("command", command))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("PhaseDB respondio HTTP {} requestId={} body={}",
                    e.getStatusCode().value(), requestId, e.getResponseBodyAsString());
            throw new PhaseException(
                    "HTTP_" + e.getStatusCode().value(),
                    "No se pudo completar la operacion con PhaseDB",
                    requestId,
                    e);
        } catch (Exception e) {
            log.error("No se pudo conectar con PhaseDB requestId={}: {}", requestId, e.getMessage(), e);
            throw new PhaseException(
                    "PHASE_CONNECTION",
                    "No se pudo conectar con PhaseDB",
                    requestId,
                    e);
        }

        if (body == null || body.isBlank()) {
            log.error("PhaseDB devolvio una respuesta vacia requestId={}", requestId);
            throw new PhaseException("PHASE_EMPTY", "PhaseDB devolvio una respuesta vacia", requestId);
        }

        PhaseResponse response;
        try {
            response = objectMapper.readValue(body, PhaseResponse.class);
        } catch (Exception e) {
            log.error("Respuesta invalida de PhaseDB requestId={}: {}", requestId, e.getMessage(), e);
            throw new PhaseException("PHASE_PARSE", "Respuesta invalida de PhaseDB", requestId, e);
        }

        if (!response.isOk()) {
            String code = response.getError() == null ? "PHASE_ERROR" : response.getError().getCode();
            String message = response.getError() == null
                    ? "Error desconocido de PhaseDB"
                    : response.getError().getMessage();
            log.warn("PhaseDB error code={} message={} requestId={}", code, message, response.getRequestId());
            throw new PhaseException(
                    code,
                    "PhaseDB rechazo la operacion",
                    response.getRequestId() == null ? requestId : response.getRequestId());
        }

        return response;
    }

    @Override
    public String execute(String command) {
        PhaseResponse response = executeRaw(command);
        return response.getOutput() == null ? "" : response.getOutput();
    }

    @Override
    public boolean health() {
        try {
            String requestId = RequestIdFilter.currentRequestId();
            String body = restClient.get()
                    .uri("/v1/health")
                    .headers(headers -> {
                        if (requestId != null) {
                            headers.set(RequestIdFilter.HEADER, requestId);
                        }
                    })
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body);
            return "ok".equalsIgnoreCase(node.path("status").asText());
        } catch (Exception e) {
            log.debug("PhaseDB health check fallo: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean exists(String global, String key) {
        String output = execute(PhaseCommands.exists(global, key)).trim();
        try {
            return Integer.parseInt(output) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String read(String global, String key) {
        try {
            return execute(PhaseCommands.readJson(global, key));
        } catch (PhaseException e) {
            if (isUndefined(e)) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public String readAll(String global) {
        try {
            return execute(PhaseCommands.readAll(global));
        } catch (PhaseException e) {
            if (isUndefined(e)) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public void writeJson(String global, String key, String json, boolean replace) {
        execute(PhaseCommands.writeJson(global, key, json, replace));
    }

    @Override
    public void writePartialJson(String global, String key, String json) {
        execute(PhaseCommands.writePartialJson(global, key, json));
    }

    @Override
    public void kill(String global, String key) {
        execute(PhaseCommands.kill(global, key));
    }

    private boolean isUndefined(PhaseException e) {
        return e.getCode() != null && e.getCode().toUpperCase().contains(UNDEFINED_VARIABLE);
    }
}
