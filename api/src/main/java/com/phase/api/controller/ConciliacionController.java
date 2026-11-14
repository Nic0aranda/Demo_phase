package com.phase.api.controller;

import com.phase.api.dto.MovimientoConciliacionDTO;
import com.phase.api.dto.ResumenConciliacionDTO;
import com.phase.api.service.ConciliacionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conciliacion")
@Tag(name = "Conciliacion bancaria", description = "Conciliacion bancaria simulada (sin CSV ni banco real)")
@SecurityRequirement(name = "bearerAuth")
public class ConciliacionController {

    private final ConciliacionService conciliacionService;

    public ConciliacionController(ConciliacionService conciliacionService) {
        this.conciliacionService = conciliacionService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResumenConciliacionDTO> resumen() {
        return ResponseEntity.ok(conciliacionService.resumen());
    }

    @GetMapping("/movimientos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MovimientoConciliacionDTO>> listarMovimientos() {
        return ResponseEntity.ok(conciliacionService.listar());
    }

    @GetMapping("/movimientos/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MovimientoConciliacionDTO> obtenerMovimiento(@PathVariable String id) {
        return ResponseEntity.ok(conciliacionService.obtener(id));
    }

    @PostMapping("/simular")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<ResumenConciliacionDTO> simular() {
        return ResponseEntity.ok(conciliacionService.simular());
    }

    @PostMapping("/ejecutar")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<ResumenConciliacionDTO> ejecutar() {
        return ResponseEntity.ok(conciliacionService.ejecutar());
    }

    @PostMapping("/movimientos/{id}/conciliar")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<MovimientoConciliacionDTO> conciliar(
            @PathVariable String id,
            @RequestBody ConciliarRequest request) {
        return ResponseEntity.ok(conciliacionService.conciliar(id, request.documentoErp()));
    }

    public record ConciliarRequest(String documentoErp) {
    }
}
