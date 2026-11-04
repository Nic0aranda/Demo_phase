package com.phase.api.controller;

import com.phase.api.dto.PresupuestoDTO;
import com.phase.api.service.PresupuestoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/presupuestos")
@Tag(name = "Presupuestos", description = "Creacion y gestion de presupuestos")
@SecurityRequirement(name = "bearerAuth")
public class PresupuestoController {

    private final PresupuestoService presupuestoService;

    public PresupuestoController(PresupuestoService presupuestoService) {
        this.presupuestoService = presupuestoService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL')")
    public ResponseEntity<PresupuestoDTO> crearPresupuesto(@Valid @RequestBody PresupuestoDTO presupuesto) {
        PresupuestoDTO creado = presupuestoService.crearPresupuesto(presupuesto);
        return ResponseEntity.created(URI.create("/api/v1/presupuestos/" + creado.getId())).body(creado);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PresupuestoDTO>> listarPresupuestos() {
        return ResponseEntity.ok(presupuestoService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PresupuestoDTO> obtenerPresupuesto(@PathVariable String id) {
        return ResponseEntity.ok(presupuestoService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL')")
    public ResponseEntity<PresupuestoDTO> actualizar(@PathVariable String id,
                                                     @Valid @RequestBody PresupuestoDTO presupuesto) {
        return ResponseEntity.ok(presupuestoService.actualizar(id, presupuesto));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL')")
    public ResponseEntity<Void> actualizarEstado(@PathVariable String id,
                                                 @RequestBody EstadoRequest request) {
        presupuestoService.actualizarEstado(id, request.estado());
        return ResponseEntity.noContent().build();
    }

    public record EstadoRequest(String estado) {
    }
}
