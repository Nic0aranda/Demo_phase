package com.phase.api.service;

import com.phase.api.common.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda los archivos de documentos de operaciones fuera de PhaseDB y expone
 * operaciones seguras de escritura, lectura y eliminacion.
 */
@Service
public class ArchivoOperacionService {

    private final Path root;
    private final long maxBytes;

    public ArchivoOperacionService(@Value("${erp.uploads.dir:uploads}") String directory,
                                   @Value("${erp.uploads.max-bytes:10485760}") long maxBytes) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el directorio de archivos: " + root, ex);
        }
    }

    public ArchivoGuardado guardar(String operacionId, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.unprocessable(
                    "ARCHIVO_REQUERIDO",
                    "Selecciona un archivo para adjuntar",
                    Map.of("archivo", "Selecciona un archivo")
            );
        }
        if (archivo.getSize() > maxBytes) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "ARCHIVO_DEMASIADO_GRANDE",
                    "El archivo supera el limite permitido",
                    Map.of("archivo", "El archivo supera el limite de 10 MB")
            );
        }

        String nombre = nombreSeguro(archivo.getOriginalFilename());
        String token = UUID.randomUUID().toString().replace("-", "") + extension(nombre);
        Path destino = carpeta(operacionId).resolve(token).normalize();
        if (!destino.startsWith(root)) {
            throw ApiException.badRequest("ARCHIVO_INVALIDO", "El nombre del archivo no es valido");
        }

        try {
            Files.createDirectories(destino.getParent());
            try (InputStream input = archivo.getInputStream()) {
                Files.copy(input, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "ARCHIVO_NO_GUARDADO",
                    "No se pudo guardar el archivo adjunto"
            );
        }
        return new ArchivoGuardado(token, nombre);
    }

    public Resource cargar(String operacionId, String token) {
        Path archivo = archivo(operacionId, token);
        if (!Files.isRegularFile(archivo)) {
            throw ApiException.notFound("Archivo no encontrado");
        }
        try {
            return new UrlResource(archivo.toUri());
        } catch (MalformedURLException ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "ARCHIVO_NO_DISPONIBLE",
                    "No se pudo leer el archivo adjunto"
            );
        }
    }

    public MediaType contentType(String operacionId, String token) {
        try {
            String detected = Files.probeContentType(archivo(operacionId, token));
            return detected == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(detected);
        } catch (IOException | IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public void eliminar(String operacionId, String token) {
        try {
            Files.deleteIfExists(archivo(operacionId, token));
        } catch (IOException | RuntimeException ignored) {
            // La limpieza de un archivo huerfano no debe ocultar el error original.
        }
    }

    private Path archivo(String operacionId, String token) {
        if (token == null || !token.matches("[a-f0-9]{32}(\\.[a-z0-9]{1,10})?")) {
            throw ApiException.notFound("Archivo no encontrado");
        }
        Path archivo = carpeta(operacionId).resolve(token).normalize();
        if (!archivo.startsWith(root)) {
            throw ApiException.notFound("Archivo no encontrado");
        }
        return archivo;
    }

    private Path carpeta(String operacionId) {
        String idSeguro = operacionId == null ? "" : operacionId.replaceAll("[^A-Za-z0-9_-]", "_");
        if (idSeguro.isBlank()) {
            throw ApiException.badRequest("OPERACION_INVALIDA", "La operacion no es valida");
        }
        Path carpeta = root.resolve("operaciones").resolve(idSeguro).normalize();
        if (!carpeta.startsWith(root)) {
            throw ApiException.badRequest("OPERACION_INVALIDA", "La operacion no es valida");
        }
        return carpeta;
    }

    private String nombreSeguro(String original) {
        String nombre = original == null ? "" : original.replace('\\', '/');
        int slash = nombre.lastIndexOf('/');
        if (slash >= 0) {
            nombre = nombre.substring(slash + 1);
        }
        nombre = nombre.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (nombre.isBlank() || nombre.equals(".") || nombre.equals("..")) {
            nombre = "documento";
        }
        if (nombre.length() > 120) {
            String ext = extension(nombre);
            nombre = nombre.substring(0, 120 - ext.length()) + ext;
        }
        return nombre;
    }

    private String extension(String nombre) {
        int dot = nombre.lastIndexOf('.');
        if (dot < 0 || dot == nombre.length() - 1) {
            return "";
        }
        String ext = nombre.substring(dot).toLowerCase(Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }

    public record ArchivoGuardado(String token, String nombre) {
    }
}
