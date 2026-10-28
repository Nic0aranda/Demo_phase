package com.phase.api.config;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Service
public class PhaseDbClient {

    private final WebClient webClient;
    private String sessionToken;

    // Quitamos el parámetro WebClient.Builder del constructor
    public PhaseDbClient() {
        // Creamos la instancia manualmente aquí
        this.webClient = WebClient.builder().baseUrl("http://127.0.0.1:8484").build();
        autenticar(); 
    }

    private void autenticar() {
        // Credenciales iniciales para obtener el token de sesión
        Map<String, String> credenciales = Map.of(
            "username", "admin", 
            "password", "admin123" // Asegúrate de que coincida con la que creaste en la consola
        );

        Map response = this.webClient.post()
            .uri("/v1/sessions")
            .bodyValue(credenciales)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (response != null && response.containsKey("token")) {
            this.sessionToken = (String) response.get("token");
        } else {
            throw new RuntimeException("No se pudo autenticar con PhaseDB");
        }
    }

    public String ejecutarComando(String comandoPhase) {
        Map<String, String> body = Map.of("command", comandoPhase);

        Map response = this.webClient.post()
            .uri("/v1/sessions/current/execute")
            .header("Authorization", "Bearer " + this.sessionToken)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .onErrorResume(e -> {
                // Si el token expira por inactividad, re-autenticamos y reintentamos
                autenticar();
                return this.webClient.post()
                    .uri("/v1/sessions/current/execute")
                    .header("Authorization", "Bearer " + this.sessionToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class);
            })
            .block();

        Boolean isOk = (Boolean) response.get("ok");
        if (isOk != null && !isOk) {
            throw new RuntimeException("Error ejecutando comando en PhaseDB: " + response.get("error"));
        }
        
        return (String) response.get("output");
    }
}