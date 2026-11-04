package com.phase.api.phase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

    @Override
    public PhaseResponse executeRaw(String command) {
        String body;
        try {
            body = restClient.post()
                    .uri("/v1/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("command", command))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new PhaseException(
                    "HTTP_" + e.getStatusCode().value(),
                    "PhaseDB respondio " + e.getStatusCode() + ": " + e.getResponseBodyAsString(),
                    null,
                    e);
        } catch (Exception e) {
            throw new PhaseException(
                    "PHASE_CONNECTION",
                    "No se pudo conectar con PhaseDB: " + e.getMessage(),
                    null,
                    e);
        }

        if (body == null || body.isBlank()) {
            throw new PhaseException("PHASE_EMPTY", "PhaseDB devolvio una respuesta vacia");
        }

        PhaseResponse response;
        try {
            response = objectMapper.readValue(body, PhaseResponse.class);
        } catch (Exception e) {
            throw new PhaseException("PHASE_PARSE", "Respuesta invalida de PhaseDB: " + e.getMessage(), null, e);
        }

        if (!response.isOk()) {
            String code = response.getError() == null ? "PHASE_ERROR" : response.getError().getCode();
            String message = response.getError() == null
                    ? "Error desconocido de PhaseDB"
                    : response.getError().getMessage();
            log.warn("PhaseDB error code={} message={} requestId={}", code, message, response.getRequestId());
            throw new PhaseException(code, message, response.getRequestId());
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
            String body = restClient.get()
                    .uri("/v1/health")
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
