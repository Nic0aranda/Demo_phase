package com.phase.api.controller;

import com.phase.api.dto.EnviarFacturaRequest;
import com.phase.api.dto.EnvioFacturaDTO;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.service.FacturaEnvioService;
import com.phase.api.service.FacturaPdfService;
import com.phase.api.service.FacturaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/facturas")
@Tag(name = "Facturas", description = "Emision y gestion de facturas")
@SecurityRequirement(name = "bearerAuth")
public class FacturaController {

    private final FacturaService facturaService;
    private final FacturaPdfService facturaPdfService;
    private final FacturaEnvioService facturaEnvioService;

    public FacturaController(FacturaService facturaService,
                             FacturaPdfService facturaPdfService,
                             FacturaEnvioService facturaEnvioService) {
        this.facturaService = facturaService;
        this.facturaPdfService = facturaPdfService;
        this.facturaEnvioService = facturaEnvioService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<FacturaDTO> emitirFactura(@Valid @RequestBody FacturaDTO factura) {
        FacturaDTO emitida = facturaService.emitirFactura(factura);
        return ResponseEntity.created(URI.create("/api/v1/facturas/" + emitida.getId())).body(emitida);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FacturaDTO>> listarFacturas() {
        return ResponseEntity.ok(facturaService.listarTodas());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FacturaDTO> obtenerFactura(@PathVariable String id) {
        return ResponseEntity.ok(facturaService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<FacturaDTO> actualizar(@PathVariable String id,
                                                 @Valid @RequestBody FacturaDTO factura) {
        return ResponseEntity.ok(facturaService.actualizar(id, factura));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<Void> actualizarEstado(@PathVariable String id,
                                                 @Valid @RequestBody EstadoRequest request) {
        facturaService.actualizarEstado(id, request.estado());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String id) {
        byte[] pdf = facturaPdfService.generar(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(id + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    @PostMapping("/{id}/enviar")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<EnvioFacturaDTO> enviarPorEmail(
            @PathVariable String id,
            @Valid @RequestBody(required = false) EnviarFacturaRequest request) {
        return ResponseEntity.ok(facturaEnvioService.enviar(id, request));
    }

    @GetMapping("/{id}/envios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EnvioFacturaDTO>> listarEnvios(@PathVariable String id) {
        return ResponseEntity.ok(facturaEnvioService.listarPorFactura(id));
    }

    public record EstadoRequest(
            @jakarta.validation.constraints.NotBlank(message = "El estado es obligatorio")
            String estado) {
    }
}
