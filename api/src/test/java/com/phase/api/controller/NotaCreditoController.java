package com.phase.api.controller;

import com.phase.api.dto.NotaCreditoDTO;
import com.phase.api.service.IdempotenciaService;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final IdempotenciaService idempotenciaService;

    public NotaCreditoController(NotaCreditoService notaCreditoService,
                                 IdempotenciaService idempotenciaService) {
        this.notaCreditoService = notaCreditoService;
        this.idempotenciaService = idempotenciaService;
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
    public ResponseEntity<NotaCreditoDTO> emitir(
            @Valid @RequestBody NotaCreditoDTO nota,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotenciaService.Clave clave = idempotenciaService.resolver(
                idempotencyKey, "nota", nota.facturaId(), nota.tipo(), nota.montoNeto(), nota.motivo());

        IdempotenciaService.Resultado<NotaCreditoDTO> resultado = idempotenciaService.ejecutar(
                clave == null ? null : clave.valor(),
                idempotenciaService.hash(nota),
                NotaCreditoDTO.class,
                clave == null ? 0 : clave.ttlSegundos(),
                () -> notaCreditoService.crear(nota));

        NotaCreditoDTO emitida = resultado.valor();
        if (resultado.replay()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replay", "true")
                    .body(emitida);
        }
        return ResponseEntity.created(URI.create("/api/v1/notas-credito/" + emitida.id())).body(emitida);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<NotaCreditoDTO> anular(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotenciaService.Clave clave = idempotenciaService.resolver(
                idempotencyKey, "nota-anular", id);

        IdempotenciaService.Resultado<NotaCreditoDTO> resultado = idempotenciaService.ejecutar(
                clave == null ? null : clave.valor(),
                idempotenciaService.hash("anular|" + id),
                NotaCreditoDTO.class,
                clave == null ? 0 : clave.ttlSegundos(),
                () -> notaCreditoService.anular(id));

        if (resultado.replay()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replay", "true")
                    .body(resultado.valor());
        }
        return ResponseEntity.ok(resultado.valor());
    }
}
