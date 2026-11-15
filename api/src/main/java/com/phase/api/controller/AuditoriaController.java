package com.phase.api.controller;

import com.phase.api.service.AuditoriaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
@Tag(name = "Auditoria temporal", description = "Consulta de solo lectura de versiones historicas")
@SecurityRequirement(name = "bearerAuth")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/transaccion-actual")
    public ResponseEntity<EstadoTemporal> transaccionActual() {
        return ResponseEntity.ok(new EstadoTemporal(auditoriaService.transaccionActual()));
    }

    @GetMapping("/historial")
    public ResponseEntity<AuditoriaService.Historial> historial(
            @RequestParam String entidad,
            @RequestParam String clave,
            @RequestParam(required = false) Long antesDe,
            @RequestParam(defaultValue = "20") int limite) {
        return ResponseEntity.ok(auditoriaService.historial(entidad, clave, antesDe, limite));
    }

    public record EstadoTemporal(String transaccionActual) {
    }
}
