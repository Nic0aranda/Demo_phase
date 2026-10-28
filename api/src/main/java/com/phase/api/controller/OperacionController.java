package com.phase.api.controller;

import com.phase.api.dto.OperacionDTO;
import com.phase.api.service.OperacionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operaciones")
public class OperacionController {

    private final OperacionService operacionService;

    public OperacionController(OperacionService operacionService) {
        this.operacionService = operacionService;
    }

    @PostMapping
    public ResponseEntity<OperacionDTO> crearOperacion(@Valid @RequestBody OperacionDTO operacion) {
        try {
            OperacionDTO creada = operacionService.crearOperacion(operacion);
            return ResponseEntity.ok(creada);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<OperacionDTO>> listarOperaciones() {
        try {
            return ResponseEntity.ok(operacionService.listarTodas());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<OperacionDTO> obtenerOperacion(@PathVariable String id) {
        try {
            OperacionDTO operacion = operacionService.obtenerPorId(id);
            if (operacion == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(operacion);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<String> actualizarEstado(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            String nuevoEstado = body.get("estado");
            if (nuevoEstado == null || nuevoEstado.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"Debe enviar el campo 'estado'\"}");
            }
            
            operacionService.actualizarEstado(id, nuevoEstado.toUpperCase());
            return ResponseEntity.ok("{\"mensaje\": \"Estado de operación actualizado a " + nuevoEstado.toUpperCase() + "\"}");
        } catch (RuntimeException e) {
            if ("OPERACION_NO_ENCONTRADA".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}