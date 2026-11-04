package com.phase.api.common;

import com.phase.api.phase.PhaseClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Secuencias legibles por tipo y anio. Para la demo el API corre en una sola
 * instancia, por eso el lock es en memoria; si se escala horizontalmente hay
 * que moverlo a un LOCK de Phase.
 */
@Service
public class SequenceService {

    private static final String GLOBAL = "SECUENCIAS";

    private final PhaseClient phaseClient;

    public SequenceService(PhaseClient phaseClient) {
        this.phaseClient = phaseClient;
    }

    public synchronized String nextId(String prefix) {
        int year = LocalDate.now().getYear();
        String key = prefix + "-" + year;

        long current = 0;
        String raw = phaseClient.read(GLOBAL, key);
        if (raw != null && !raw.isBlank()) {
            try {
                current = Long.parseLong(raw.trim().replace("\"", ""));
            } catch (NumberFormatException ignored) {
                current = 0;
            }
        }

        long next = current + 1;
        phaseClient.writeJson(GLOBAL, key, Long.toString(next), true);
        return String.format("%s-%d-%04d", prefix, year, next);
    }
}
