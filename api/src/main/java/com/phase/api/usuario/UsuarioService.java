package com.phase.api.usuario;

import tools.jackson.databind.ObjectMapper;
import com.phase.api.common.ApiException;
import com.phase.api.common.PageResponse;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import com.phase.api.security.EstadoUsuario;
import com.phase.api.security.Rol;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class UsuarioService {

    public static final String GLOBAL = "USUARIOS";

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(PhaseClient phaseClient, ObjectMapper objectMapper, PasswordEncoder passwordEncoder) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean existe(String email) {
        String key = normalizarEmail(email);
        return key != null && phaseClient.exists(GLOBAL, key);
    }

    public UsuarioRecord obtenerRegistro(String email) {
        String key = normalizarEmail(email);
        if (key == null || key.isBlank()) {
            return null;
        }
        String json = phaseClient.read(GLOBAL, key);
        return PhaseJson.toObject(json, UsuarioRecord.class, objectMapper);
    }

    public UsuarioDTO obtener(String email) {
        UsuarioRecord record = obtenerRegistro(email);
        if (record == null) {
            throw ApiException.notFound("Usuario no encontrado: " + email);
        }
        return UsuarioDTO.from(record);
    }

    public PageResponse<UsuarioDTO> listar(String q, Rol rol, EstadoUsuario estado, int page, int size) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<UsuarioDTO> usuarios = new ArrayList<>();

        for (UsuarioRecord record : listarRegistros().values()) {
            if (rol != null && record.getRol() != rol) {
                continue;
            }
            if (estado != null && record.getEstado() != estado) {
                continue;
            }
            if (!query.isEmpty() && !coincide(record, query)) {
                continue;
            }
            usuarios.add(UsuarioDTO.from(record));
        }

        usuarios.sort(Comparator.comparing(UsuarioDTO::getEmail, Comparator.nullsLast(String::compareToIgnoreCase)));
        return PageResponse.of(usuarios, page, size);
    }

    public UsuarioDTO crear(UsuarioDTO dto) {
        String email = normalizarEmail(dto.getEmail());
        if (email == null || email.isBlank()) {
            throw ApiException.badRequest("EMAIL_REQUERIDO", "El correo es obligatorio");
        }
        if (existe(email)) {
            throw ApiException.conflict("USUARIO_EXISTE", "Ya existe un usuario con el correo " + email);
        }
        validarPassword(dto.getPassword(), dto.getPasswordConfirm());

        UsuarioRecord record = new UsuarioRecord();
        record.setEmail(email);
        record.setNombres(dto.getNombres().trim());
        record.setApellidos(dto.getApellidos().trim());
        record.setRol(dto.getRol() == null ? Rol.COMERCIAL : dto.getRol());
        record.setEstado(dto.getEstado() == null ? EstadoUsuario.ACTIVO : dto.getEstado());
        record.setPermisos(normalizarPermisos(dto.getPermisos()));
        record.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

        guardar(record);
        return UsuarioDTO.from(record);
    }

    public UsuarioDTO actualizar(String email, UsuarioDTO dto) {
        String key = normalizarEmail(email);
        UsuarioRecord record = obtenerRegistro(key);
        if (record == null) {
            throw ApiException.notFound("Usuario no encontrado: " + email);
        }
        if (dto.getEmail() != null && !key.equals(normalizarEmail(dto.getEmail()))) {
            throw ApiException.badRequest("EMAIL_INMUTABLE", "El correo del usuario no se puede cambiar");
        }

        if (dto.getNombres() != null && !dto.getNombres().isBlank()) {
            record.setNombres(dto.getNombres().trim());
        }
        if (dto.getApellidos() != null && !dto.getApellidos().isBlank()) {
            record.setApellidos(dto.getApellidos().trim());
        }
        if (dto.getRol() != null) {
            record.setRol(dto.getRol());
        }
        if (dto.getEstado() != null) {
            record.setEstado(dto.getEstado());
        }
        if (dto.getPermisos() != null) {
            record.setPermisos(normalizarPermisos(dto.getPermisos()));
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            validarPassword(dto.getPassword(), dto.getPasswordConfirm());
            record.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }

        guardar(record);
        return UsuarioDTO.from(record);
    }

    public UsuarioDTO cambiarEstado(String email, EstadoUsuario estado) {
        if (estado == null) {
            throw ApiException.badRequest("ESTADO_REQUERIDO", "Debes indicar el estado");
        }
        UsuarioRecord record = obtenerRegistro(email);
        if (record == null) {
            throw ApiException.notFound("Usuario no encontrado: " + email);
        }
        record.setEstado(estado);
        guardar(record);
        return UsuarioDTO.from(record);
    }

    public UsuarioDTO cambiarPermisos(String email, List<String> permisos) {
        UsuarioRecord record = obtenerRegistro(email);
        if (record == null) {
            throw ApiException.notFound("Usuario no encontrado: " + email);
        }
        record.setPermisos(normalizarPermisos(permisos));
        guardar(record);
        return UsuarioDTO.from(record);
    }

    public void eliminar(String email) {
        UsuarioRecord record = obtenerRegistro(email);
        if (record == null) {
            throw ApiException.notFound("Usuario no encontrado: " + email);
        }
        record.setEstado(EstadoUsuario.INACTIVO);
        guardar(record);
    }

    public void cambiarPassword(String email, String actual, String nueva, String confirmar) {
        UsuarioRecord record = obtenerRegistro(email);
        if (record == null) {
            throw ApiException.notFound("Usuario no encontrado: " + email);
        }
        if (!passwordEncoder.matches(actual == null ? "" : actual, record.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "PASSWORD_INCORRECTA", "La contrasena actual no es correcta");
        }
        validarPassword(nueva, confirmar);
        record.setPasswordHash(passwordEncoder.encode(nueva));
        guardar(record);
    }

    public void crearSeed(String email, String password, String nombres, String apellidos) {
        String key = normalizarEmail(email);
        if (existe(key)) {
            return;
        }
        if (password == null || password.length() < 8) {
            throw new IllegalStateException("La contrasena del admin seed debe tener al menos 8 caracteres");
        }
        UsuarioRecord record = new UsuarioRecord();
        record.setEmail(key);
        record.setNombres(nombres);
        record.setApellidos(apellidos);
        record.setRol(Rol.ADMIN);
        record.setEstado(EstadoUsuario.ACTIVO);
        record.setPermisos(List.of());
        record.setPasswordHash(passwordEncoder.encode(password));
        guardar(record);
    }

    private Map<String, UsuarioRecord> listarRegistros() {
        String json = phaseClient.readAll(GLOBAL);
        return PhaseJson.toMap(json, UsuarioRecord.class, objectMapper);
    }

    private void guardar(UsuarioRecord record) {
        String json = PhaseJson.toJson(record, objectMapper);
        phaseClient.writeJson(GLOBAL, record.getEmail(), json, true);
    }

    private boolean coincide(UsuarioRecord record, String query) {
        return contains(record.getEmail(), query)
                || contains(record.getNombres(), query)
                || contains(record.getApellidos(), query)
                || contains(record.getNombreCompleto(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void validarPassword(String password, String confirm) {
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("PASSWORD_REQUERIDA", "La contrasena es obligatoria");
        }
        if (password.length() < 8 || password.length() > 72) {
            throw ApiException.badRequest("PASSWORD_INVALIDA", "La contrasena debe tener entre 8 y 72 caracteres");
        }
        if (confirm == null || !Objects.equals(password, confirm)) {
            throw ApiException.badRequest("PASSWORD_NO_COINCIDE", "Las contrasenas no coinciden");
        }
    }

    private List<String> normalizarPermisos(List<String> permisos) {
        if (permisos == null) {
            return new ArrayList<>();
        }
        return permisos.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
