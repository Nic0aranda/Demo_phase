package com.phase.api.common;

import com.phase.api.phase.PhaseClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Secuencias legibles por tipo y anio.
 *
 * <p>El lock por prefijo evita que dos requests simultaneos lean el mismo valor
 * y generen un ID duplicado. Sigue siendo un lock de una sola instancia; si se
 * escala horizontalmente hay que moverlo a un LOCK nativo de Phase.</p>
 */
@Service
public class SequenceService {

    private static final String GLOBAL = "SECUENCIAS";

    private final PhaseClient phaseClient;
    private final PhaseLockService lockService;

    public SequenceService(PhaseClient phaseClient, PhaseLockService lockService) {
        this.phaseClient = phaseClient;
        this.lockService = lockService;
    }

    public String nextId(String prefix) {
        int year = LocalDate.now().getYear();
        String key = prefix + "-" + year;

        return lockService.conLock("SECUENCIA:" + key, () -> {
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
        });
    }
}
