package com.phase.api.controller;

import com.phase.api.dto.OperacionDTO;
import com.phase.api.service.OperacionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/operaciones")
@Tag(name = "Operaciones", description = "Gestion de operaciones de transporte")
@SecurityRequirement(name = "bearerAuth")
public class OperacionController {

    private final OperacionService operacionService;

    public OperacionController(OperacionService operacionService) {
        this.operacionService = operacionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERACIONES')")
    public ResponseEntity<OperacionDTO> crearOperacion(@Valid @RequestBody OperacionDTO operacion) {
        OperacionDTO creada = operacionService.crearOperacion(operacion);
        return ResponseEntity.created(URI.create("/api/v1/operaciones/" + creada.getId())).body(creada);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OperacionDTO>> listarOperaciones() {
        return ResponseEntity.ok(operacionService.listarTodas());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OperacionDTO> obtenerOperacion(@PathVariable String id) {
        return ResponseEntity.ok(operacionService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERACIONES')")
    public ResponseEntity<OperacionDTO> actualizar(@PathVariable String id,
                                                   @Valid @RequestBody OperacionDTO operacion) {
        return ResponseEntity.ok(operacionService.actualizar(id, operacion));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','OPERACIONES')")
    public ResponseEntity<Void> actualizarEstado(@PathVariable String id,
                                                 @RequestBody EstadoRequest request) {
        operacionService.actualizarEstado(id, request.estado());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/documentos")
    @PreAuthorize("hasAnyRole('ADMIN','OPERACIONES')")
    public ResponseEntity<OperacionDTO> agregarDocumento(
            @PathVariable String id,
            @Valid @RequestBody OperacionDTO.DocumentoOperacionDTO documento) {
        return ResponseEntity.ok(operacionService.agregarDocumento(id, documento));
    }

    @PostMapping(value = "/{id}/documentos/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','OPERACIONES')")
    public ResponseEntity<OperacionDTO> agregarDocumentoConArchivo(
            @PathVariable String id,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) String fecha,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo) {
        return ResponseEntity.ok(operacionService.agregarDocumentoConArchivo(id, tipo, numero, fecha, archivo));
    }

    @GetMapping("/{id}/documentos/{token}/archivo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> descargarArchivo(
            @PathVariable String id,
            @PathVariable String token) {
        OperacionService.DocumentoArchivo archivo = operacionService.obtenerArchivo(id, token);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(archivo.nombre(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(archivo.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(archivo.recurso());
    }

    public record EstadoRequest(String estado) {
    }
}
