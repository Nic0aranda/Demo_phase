package com.phase.api.usuario;

import com.phase.api.common.PageResponse;
import com.phase.api.security.EstadoUsuario;
import com.phase.api.security.Rol;
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
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuarios", description = "Gestion de usuarios y roles del ERP")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<UsuarioDTO>> listar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Rol rol,
            @RequestParam(required = false) EstadoUsuario estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(usuarioService.listar(q, rol, estado, safePage, safeSize));
    }

    @GetMapping("/{email}")
    public ResponseEntity<UsuarioDTO> obtener(@PathVariable String email) {
        return ResponseEntity.ok(usuarioService.obtener(email));
    }

    @PostMapping
    public ResponseEntity<UsuarioDTO> crear(@Valid @RequestBody UsuarioDTO dto) {
        UsuarioDTO creado = usuarioService.crear(dto);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + creado.getEmail())).body(creado);
    }

    @PutMapping("/{email}")
    public ResponseEntity<UsuarioDTO> actualizar(@PathVariable String email,
                                                 @Valid @RequestBody UsuarioDTO dto) {
        return ResponseEntity.ok(usuarioService.actualizar(email, dto));
    }

    @PatchMapping("/{email}/estado")
    public ResponseEntity<UsuarioDTO> cambiarEstado(@PathVariable String email,
                                                    @RequestBody EstadoRequest request) {
        return ResponseEntity.ok(usuarioService.cambiarEstado(email, request.estado()));
    }

    @PatchMapping("/{email}/permisos")
    public ResponseEntity<UsuarioDTO> cambiarPermisos(@PathVariable String email,
                                                      @RequestBody PermisosRequest request) {
        return ResponseEntity.ok(usuarioService.cambiarPermisos(email, request.permisos()));
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> eliminar(@PathVariable String email) {
        usuarioService.eliminar(email);
        return ResponseEntity.noContent().build();
    }

    public record EstadoRequest(EstadoUsuario estado) {
    }

    public record PermisosRequest(List<String> permisos) {
    }
}
