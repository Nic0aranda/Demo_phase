package com.phase.api.controller;

import com.phase.api.common.PageResponse;
import com.phase.api.dto.ClienteDTO;
import com.phase.api.service.ClienteService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes", description = "CRUD de clientes")
@SecurityRequirement(name = "bearerAuth")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ClienteDTO>> listar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(clienteService.listar(q, activo, safePage, safeSize));
    }

    @GetMapping("/buscar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ClienteDTO>> buscar(@RequestParam("q") String q) {
        return ResponseEntity.ok(clienteService.buscarPorNombre(q));
    }

    @GetMapping("/{numDoc}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClienteDTO> obtener(@PathVariable String numDoc) {
        return ResponseEntity.ok(clienteService.obtener(numDoc));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL')")
    public ResponseEntity<ClienteDTO> crear(@Valid @RequestBody ClienteDTO cliente) {
        ClienteDTO creado = clienteService.crear(cliente);
        return ResponseEntity.created(URI.create("/api/v1/clientes/" + creado.getNumDoc())).body(creado);
    }

    @PutMapping("/{numDoc}")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL')")
    public ResponseEntity<ClienteDTO> actualizar(@PathVariable String numDoc,
                                                 @Valid @RequestBody ClienteDTO cliente) {
        return ResponseEntity.ok(clienteService.actualizar(numDoc, cliente));
    }

    @PatchMapping("/{numDoc}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL')")
    public ResponseEntity<ClienteDTO> cambiarEstado(@PathVariable String numDoc,
                                                    @RequestBody EstadoRequest request) {
        clienteService.cambiarActivo(numDoc, request.activo());
        return ResponseEntity.ok(clienteService.obtener(numDoc));
    }

    @DeleteMapping("/{numDoc}")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL')")
    public ResponseEntity<Void> desactivar(@PathVariable String numDoc) {
        clienteService.desactivar(numDoc);
        return ResponseEntity.noContent().build();
    }

    public record EstadoRequest(boolean activo) {
    }
}
