package com.phase.api.controller;

import com.phase.api.dto.PagoDTO;
import com.phase.api.service.IdempotenciaService;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final IdempotenciaService idempotenciaService;

    public PagoController(PagoService pagoService, IdempotenciaService idempotenciaService) {
        this.pagoService = pagoService;
        this.idempotenciaService = idempotenciaService;
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
    public ResponseEntity<PagoDTO> registrar(
            @Valid @RequestBody PagoDTO pago,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotenciaService.Clave clave = idempotenciaService.resolver(
                idempotencyKey, "pago", pago.facturaId(), pago.monto(), pago.referencia());

        IdempotenciaService.Resultado<PagoDTO> resultado = idempotenciaService.ejecutar(
                clave == null ? null : clave.valor(),
                idempotenciaService.hash(pago),
                PagoDTO.class,
                clave == null ? 0 : clave.ttlSegundos(),
                () -> pagoService.registrar(pago));

        PagoDTO registrado = resultado.valor();
        if (resultado.replay()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replay", "true")
                    .body(registrado);
        }
        return ResponseEntity.created(URI.create("/api/v1/pagos/" + registrado.id())).body(registrado);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<PagoDTO> anular(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotenciaService.Clave clave = idempotenciaService.resolver(
                idempotencyKey, "pago-anular", id);

        IdempotenciaService.Resultado<PagoDTO> resultado = idempotenciaService.ejecutar(
                clave == null ? null : clave.valor(),
                idempotenciaService.hash("anular|" + id),
                PagoDTO.class,
                clave == null ? 0 : clave.ttlSegundos(),
                () -> pagoService.anular(id));

        if (resultado.replay()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replay", "true")
                    .body(resultado.valor());
        }
        return ResponseEntity.ok(resultado.valor());
    }
}
