package com.phase.api.cliente;

import com.phase.api.config.MigrationProperties;
import com.phase.api.dto.ClienteDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.service.ClienteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(2)
public class ClienteMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClienteMigration.class);
    private static final String MIGRATION_GLOBAL = "MIGRACION";
    private static final String MIGRATION_KEY = "clientes-v1";

    private final MigrationProperties migrationProperties;
    private final ClienteService clienteService;
    private final PhaseClient phaseClient;

    public ClienteMigration(MigrationProperties migrationProperties,
                            ClienteService clienteService,
                            PhaseClient phaseClient) {
        this.migrationProperties = migrationProperties;
        this.clienteService = clienteService;
        this.phaseClient = phaseClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrationProperties.isEnabled() || !migrationProperties.isClientes()) {
            log.info("Migracion de clientes deshabilitada");
            return;
        }
        try {
            if (phaseClient.exists(MIGRATION_GLOBAL, MIGRATION_KEY)) {
                log.info("Migracion de clientes ya aplicada");
                return;
            }

            Map<String, ClienteDTO> clientes = clienteService.listarTodosRaw();
            int migrated = 0;
            for (Map.Entry<String, ClienteDTO> entry : clientes.entrySet()) {
                clienteService.migrar(entry.getKey(), entry.getValue());
                migrated++;
            }

            phaseClient.writeJson(MIGRATION_GLOBAL, MIGRATION_KEY, "{\"ok\":true}", true);
            log.info("Migracion de clientes completada: {} registros", migrated);
        } catch (Exception e) {
            log.warn("No se pudo ejecutar la migracion de clientes: {}", e.getMessage());
        }
    }
}
