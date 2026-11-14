package com.phase.api.config;

import com.phase.api.phase.PhaseClient;
import org.springframework.stereotype.Service;

/**
 * Adaptador temporal para los servicios que todavia usan la firma antigua.
 * Los modulos nuevos usan {@link PhaseClient} directamente.
 *
 * @deprecated migrar los servicios restantes a PhaseClient.
 */
@Deprecated
@Service
public class PhaseDbClient {

    private final PhaseClient phaseClient;

    public PhaseDbClient(PhaseClient phaseClient) {
        this.phaseClient = phaseClient;
    }

    public String ejecutarComando(String comandoPhase) {
        return phaseClient.execute(comandoPhase);
    }
}
