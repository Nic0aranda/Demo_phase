package com.phase.api.service;

import com.phase.api.config.PhaseDbClient;
import com.phase.api.dto.ClienteDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClienteService {

    private final PhaseDbClient phaseDbClient;
    private final ObjectMapper objectMapper;

    public ClienteService(PhaseDbClient phaseDbClient) {
        this.phaseDbClient = phaseDbClient;
        this.objectMapper = new ObjectMapper();
    }

    // 1. Crear o actualizar cliente completo
    public void guardarCliente(ClienteDTO cliente) throws Exception {
        String jsonPayload = objectMapper.writeValueAsString(cliente);
        String comando = String.format("ZMERGE ^CLIENTES(\"%s\")=%s", cliente.getRut(), jsonPayload);
        phaseDbClient.ejecutarComando(comando);
    }

    // 2. Obtener un cliente por su RUT
    public ClienteDTO obtenerClientePorRut(String rut) {
    try {
        String comando = String.format("ZWRITE ^CLIENTES(\"%s\")", rut);
        String respuesta = phaseDbClient.ejecutarComando(comando);
        if (respuesta == null || respuesta.trim().isEmpty() || respuesta.trim().equals("null")) {
            return null;
            }
        return objectMapper.readValue(respuesta.trim(), ClienteDTO.class);
        } catch (Exception e) {
        // Si PhaseDB arroja excepción (nodo no existe), retornamos null
        return null; 
        }
    }

    // 3. Obtener todos los clientes
    public List<ClienteDTO> listarTodos() throws Exception {
        List<ClienteDTO> clientes = new ArrayList<>();
        String comando = "ZWRITE ^CLIENTES";
        String respuesta = phaseDbClient.ejecutarComando(comando).trim();
        
        if (respuesta.isEmpty() || respuesta.equals("null") || respuesta.startsWith("Error")) {
            return clientes;
        }

        var node = objectMapper.readTree(respuesta);
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                try {
                    if (entry.getValue().isObject() && entry.getValue().has("rut")) {
                        ClienteDTO cliente = objectMapper.treeToValue(entry.getValue(), ClienteDTO.class);
                        clientes.add(cliente);
                    }
                } catch (Exception e) {
                    e.addSuppressed(new RuntimeException("Error al procesar cliente con RUT: " + entry.getKey(), e));
                }
            });
        }
        return clientes;
    }

    //Buscar clientes por coincidencia en el nombre
    public List<ClienteDTO> buscarPorNombre(String filtro) throws Exception {
        List<ClienteDTO> todos = listarTodos();
        List<ClienteDTO> filtrados = new ArrayList<>();
        String query = filtro.toLowerCase();
        
        for (ClienteDTO c : todos) {
            if (c.getNombre() != null && c.getNombre().toLowerCase().contains(query)) {
                filtrados.add(c);
            }
        }
        return filtrados;
    }

    // 5. Borrado Lógico (Desactivar)
    public void desactivarCliente(String rut) throws Exception {
    ClienteDTO existente = obtenerClientePorRut(rut);
    if (existente == null) {
        throw new RuntimeException("CLIENTE_NO_ENCONTRADO");
        }

    // Actualizamos únicamente la propiedad 'activo' a false
    String comando = String.format("ZMERGE ^CLIENTES(\"%s\")={\"activo\":false}", rut);
    phaseDbClient.ejecutarComando(comando);
    }
}