package com.phase.api.controller;

import com.phase.api.dto.FacturaDTO;
import com.phase.api.service.FacturaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/facturas")
public class FacturaController {

    private final FacturaService facturaService;

    public FacturaController(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    @PostMapping
    public ResponseEntity<FacturaDTO> emitirFactura(@Valid @RequestBody FacturaDTO factura) {
        try {
            FacturaDTO emitida = facturaService.emitirFactura(factura);
            return ResponseEntity.ok(emitida);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<FacturaDTO>> listarFacturas() {
        try {
            return ResponseEntity.ok(facturaService.listarTodas());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacturaDTO> obtenerFactura(@PathVariable String id) {
        try {
            FacturaDTO factura = facturaService.obtenerPorId(id);
            if (factura == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(factura);
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
            
            facturaService.actualizarEstado(id, nuevoEstado.toUpperCase());
            return ResponseEntity.ok("{\"mensaje\": \"Estado de factura actualizado a " + nuevoEstado.toUpperCase() + "\"}");
        } catch (RuntimeException e) {
            if ("FACTURA_NO_ENCONTRADA".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}