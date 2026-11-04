package com.phase.api.controller;

import com.phase.api.dto.PagoDTO;
import com.phase.api.service.PagoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pagos")
@Tag(name = "Pagos", description = "Registro y anulacion de pagos de facturas")
@SecurityRequirement(name = "bearerAuth")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PagoDTO>> listar(@RequestParam(required = false) String facturaId) {
        if (facturaId == null || facturaId.isBlank()) {
            return ResponseEntity.ok(pagoService.listarTodos());
        }
        return ResponseEntity.ok(pagoService.listarPorFactura(facturaId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagoDTO> obtener(@PathVariable String id) {
        return ResponseEntity.ok(pagoService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<PagoDTO> registrar(@Valid @RequestBody PagoDTO pago) {
        PagoDTO registrado = pagoService.registrar(pago);
        return ResponseEntity.created(URI.create("/api/v1/pagos/" + registrado.id())).body(registrado);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<PagoDTO> anular(@PathVariable String id) {
        return ResponseEntity.ok(pagoService.anular(id));
    }
}
