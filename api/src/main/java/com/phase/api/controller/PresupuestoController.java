package com.phase.api.controller;

import com.phase.api.dto.PresupuestoDTO;
import com.phase.api.service.PresupuestoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/presupuestos")
public class PresupuestoController {

    private final PresupuestoService presupuestoService;

    public PresupuestoController(PresupuestoService presupuestoService) {
        this.presupuestoService = presupuestoService;
    }

    @PostMapping
    public ResponseEntity<PresupuestoDTO> crearPresupuesto(@Valid @RequestBody PresupuestoDTO presupuesto) {
        try {
            PresupuestoDTO creado = presupuestoService.crearPresupuesto(presupuesto);
            return ResponseEntity.ok(creado);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<PresupuestoDTO>> listarPresupuestos() {
        try {
            return ResponseEntity.ok(presupuestoService.listarTodos());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<PresupuestoDTO> obtenerPresupuesto(@PathVariable String id) {
        try {
            PresupuestoDTO presupuesto = presupuestoService.obtenerPorId(id);
            if (presupuesto == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(presupuesto);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // PATCH se usa en REST para actualizaciones parciales (solo cambiamos el estado)
    @PatchMapping("/{id}/estado")
    public ResponseEntity<String> actualizarEstado(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            String nuevoEstado = body.get("estado");
            if (nuevoEstado == null || nuevoEstado.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"Debe enviar el campo 'estado'\"}");
            }
            
            presupuestoService.actualizarEstado(id, nuevoEstado.toUpperCase());
            return ResponseEntity.ok("{\"mensaje\": \"Estado actualizado a " + nuevoEstado.toUpperCase() + "\"}");
            
        } catch (RuntimeException e) {
            if ("PRESUPUESTO_NO_ENCONTRADO".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}