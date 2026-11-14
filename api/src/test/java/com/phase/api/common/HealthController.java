package com.phase.api.common;

import com.phase.api.phase.PhaseClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Estado del API y de PhaseDB")
public class HealthController {

    private final PhaseClient phaseClient;

    public HealthController(PhaseClient phaseClient) {
        this.phaseClient = phaseClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean phaseUp = phaseClient.health();
        Map<String, Object> body = Map.of(
                "status", phaseUp ? "ok" : "degraded",
                "phase", phaseUp ? "up" : "down",
                "timestamp", Instant.now().toString()
        );
        return phaseUp
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(503).body(body);
    }
}
