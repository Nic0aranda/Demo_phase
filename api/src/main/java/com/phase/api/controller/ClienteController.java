package com.phase.api.controller;

import com.phase.api.dto.ClienteDTO;
import com.phase.api.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    // create cliente completo
    @PostMapping
    public ResponseEntity<String> crearCliente(@Valid @RequestBody ClienteDTO cliente) {
        try {
            // Aseguramos que al crear siempre nazca activo
            cliente.setActivo(true);
            clienteService.guardarCliente(cliente);
            return ResponseEntity.ok("{\"mensaje\": \"Cliente creado exitosamente\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    // put cliente completo
    @PutMapping("/{rut}")
    public ResponseEntity<String> actualizarCliente(@PathVariable String rut, @Valid @RequestBody ClienteDTO cliente) {
        try {
            // Verificamos existencia previa
            if (clienteService.obtenerClientePorRut(rut) == null) {
                return ResponseEntity.notFound().build();
            }
            // Forzamos que el RUT del body coincida con el de la URL por seguridad
            cliente.setRut(rut);
            clienteService.guardarCliente(cliente);
            return ResponseEntity.ok("{\"mensaje\": \"Cliente actualizado exitosamente\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    // get all clientes
    @GetMapping
    public ResponseEntity<List<ClienteDTO>> listarClientes() {
        try {
            List<ClienteDTO> clientes = clienteService.listarTodos();
            return ResponseEntity.ok(clientes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // get by rut
    @GetMapping("/{rut}")
    public ResponseEntity<ClienteDTO> obtenerClientePorRut(@PathVariable String rut) {
        try {
            ClienteDTO cliente = clienteService.obtenerClientePorRut(rut);
            if (cliente == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(cliente);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // get by name filter
    @GetMapping("/buscar")
    public ResponseEntity<List<ClienteDTO>> buscarClientes(@RequestParam("q") String query) {
        try {
            List<ClienteDTO> resultados = clienteService.buscarPorNombre(query);
            return ResponseEntity.ok(resultados);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Delete (soft)
    @DeleteMapping("/{rut}")
    public ResponseEntity<String> desactivarCliente(@PathVariable String rut) {
        try {
            clienteService.desactivarCliente(rut);
            return ResponseEntity.ok("{\"mensaje\": \"Cliente desactivado exitosamente\"}");
        } catch (RuntimeException e) {
            if ("CLIENTE_NO_ENCONTRADO".equals(e.getMessage())) {
                return ResponseEntity.status(404).body("{\"error\": \"El cliente con el RUT especificado no existe\"}");
            }
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
