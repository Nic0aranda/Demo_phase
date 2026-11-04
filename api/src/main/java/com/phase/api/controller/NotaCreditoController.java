package com.phase.api.controller;

import com.phase.api.dto.NotaCreditoDTO;
import com.phase.api.service.NotaCreditoService;
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
@RequestMapping("/api/v1/notas-credito")
@Tag(name = "Notas de credito", description = "Emision y anulacion de notas de credito")
@SecurityRequirement(name = "bearerAuth")
public class NotaCreditoController {

    private final NotaCreditoService notaCreditoService;

    public NotaCreditoController(NotaCreditoService notaCreditoService) {
        this.notaCreditoService = notaCreditoService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotaCreditoDTO>> listar(
            @RequestParam(required = false) String facturaId) {
        if (facturaId == null || facturaId.isBlank()) {
            return ResponseEntity.ok(notaCreditoService.listar());
        }
        return ResponseEntity.ok(notaCreditoService.listarPorFactura(facturaId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotaCreditoDTO> obtener(@PathVariable String id) {
        return ResponseEntity.ok(notaCreditoService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<NotaCreditoDTO> emitir(@Valid @RequestBody NotaCreditoDTO nota) {
        NotaCreditoDTO emitida = notaCreditoService.crear(nota);
        return ResponseEntity.created(URI.create("/api/v1/notas-credito/" + emitida.id())).body(emitida);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<NotaCreditoDTO> anular(@PathVariable String id) {
        return ResponseEntity.ok(notaCreditoService.anular(id));
    }
}
