package com.phase.api.controller;

import com.phase.api.dto.PromesaPagoDTO;
import com.phase.api.dto.RecordatorioDTO;
import com.phase.api.service.CobranzaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cobranza")
@Tag(name = "Cobranza", description = "Recordatorios de pago y promesas de clientes")
@SecurityRequirement(name = "bearerAuth")
public class CobranzaController {

    private final CobranzaService cobranzaService;

    public CobranzaController(CobranzaService cobranzaService) {
        this.cobranzaService = cobranzaService;
    }

    @GetMapping("/recordatorios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RecordatorioDTO>> listarRecordatorios(
            @RequestParam(required = false) String facturaId) {
        return ResponseEntity.ok(cobranzaService.listarRecordatorios(facturaId));
    }

    @PostMapping("/recordatorios")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<RecordatorioDTO> enviarRecordatorio(
            @Valid @RequestBody RecordatorioDTO recordatorio) {
        RecordatorioDTO enviado = cobranzaService.enviarRecordatorio(recordatorio);
        return ResponseEntity.created(URI.create("/api/v1/cobranza/recordatorios/" + enviado.id()))
                .body(enviado);
    }

    @PostMapping("/recordatorios/masivo")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<List<RecordatorioDTO>> enviarRecordatoriosMasivos() {
        return ResponseEntity.ok(cobranzaService.enviarRecordatoriosMasivos());
    }

    @GetMapping("/promesas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PromesaPagoDTO>> listarPromesas(
            @RequestParam(required = false) String facturaId) {
        return ResponseEntity.ok(cobranzaService.listarPromesas(facturaId));
    }

    @PostMapping("/promesas")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<PromesaPagoDTO> registrarPromesa(
            @Valid @RequestBody PromesaPagoDTO promesa) {
        PromesaPagoDTO registrada = cobranzaService.registrarPromesa(promesa);
        return ResponseEntity.created(URI.create("/api/v1/cobranza/promesas/" + registrada.id()))
                .body(registrada);
    }

    @PatchMapping("/promesas/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<PromesaPagoDTO> actualizarEstadoPromesa(
            @PathVariable String id,
            @RequestBody EstadoRequest request) {
        return ResponseEntity.ok(cobranzaService.actualizarEstadoPromesa(id, request.estado()));
    }

    public record EstadoRequest(String estado) {
    }
}
