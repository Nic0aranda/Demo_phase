package com.phase.api.usuario;

import com.phase.api.config.SeedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final SeedProperties seedProperties;
    private final UsuarioService usuarioService;

    public AdminSeeder(SeedProperties seedProperties, UsuarioService usuarioService) {
        this.seedProperties = seedProperties;
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.isEnabled()) {
            log.info("Seed de admin deshabilitado");
            return;
        }
        try {
            if (usuarioService.existe(seedProperties.getAdminEmail())) {
                log.info("Usuario admin ya existe: {}", seedProperties.getAdminEmail());
                return;
            }
            usuarioService.crearSeed(
                    seedProperties.getAdminEmail(),
                    seedProperties.getAdminPassword(),
                    seedProperties.getAdminName(),
                    seedProperties.getAdminApellidos()
            );
            log.info("Usuario admin sembrado: {}", seedProperties.getAdminEmail());
        } catch (Exception e) {
            log.warn("No se pudo sembrar el usuario admin (PhaseDB no disponible?): {}", e.getMessage());
        }
    }
}
