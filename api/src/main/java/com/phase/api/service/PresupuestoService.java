package com.phase.api.service;

import com.phase.api.config.PhaseDbClient;
import com.phase.api.dto.PresupuestoDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PresupuestoService {

    private final PhaseDbClient phaseDbClient;
    private final ObjectMapper objectMapper;

    public PresupuestoService(PhaseDbClient phaseDbClient) {
        this.phaseDbClient = phaseDbClient;
        this.objectMapper = new ObjectMapper();
    }

    public PresupuestoDTO crearPresupuesto(PresupuestoDTO presupuesto) throws Exception {
        // 1. Generar ID si no viene (Ej: PRE-5f3a...)
        if (presupuesto.getId() == null || presupuesto.getId().isEmpty()) {
            presupuesto.setId("PRE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // 2. Recalcular valores financieros (Lógica de ERP)
        double subtotal = 0;
        for (PresupuestoDTO.ItemPresupuestoDTO item : presupuesto.getItems()) {
            double totalLinea = item.getCantidad() * item.getPrecioUnitario();
            item.setTotalLinea(totalLinea);
            subtotal += totalLinea;
        }
        
        presupuesto.setSubtotal(subtotal);
        presupuesto.setImpuestos(subtotal * 0.19); // Aplicar IVA 19%
        presupuesto.setTotal(subtotal + presupuesto.getImpuestos());

        // 3. Guardar en PhaseDB
        String jsonPayload = objectMapper.writeValueAsString(presupuesto);
        String comando = String.format("ZMERGE ^PRESUPUESTOS(\"%s\")=%s", presupuesto.getId(), jsonPayload);
        phaseDbClient.ejecutarComando(comando);

        return presupuesto;
    }

    // OBTENER TODOS
    public List<PresupuestoDTO> listarTodos() {
        List<PresupuestoDTO> presupuestos = new ArrayList<>();
        try {
            String comando = "ZWRITE ^PRESUPUESTOS";
            String respuesta = phaseDbClient.ejecutarComando(comando);
            
            if (respuesta == null || respuesta.trim().isEmpty() || respuesta.equals("null")) {
                return presupuestos;
            }

            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(respuesta);
            if (rootNode.isObject()) {
                rootNode.fields().forEachRemaining(entry -> {
                    com.fasterxml.jackson.databind.JsonNode presNode = entry.getValue();
                    if (presNode.isObject() && presNode.has("id")) {
                        presupuestos.add(parsearPresupuesto(presNode));
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error en listarTodos: " + e.getMessage());
        }
        return presupuestos;
    }

    // OBTENER POR ID
    public PresupuestoDTO obtenerPorId(String id) {
        String idLimpio = id.replace("\"", "").trim();
        
        try {
            String comando = String.format("ZWRITE ^PRESUPUESTOS(\"%s\")", idLimpio);
            String respuesta = phaseDbClient.ejecutarComando(comando);
            
            if (respuesta == null || respuesta.trim().isEmpty() || respuesta.equals("null")) {
                return null;
            }

            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(respuesta);
            
            // Caso 1: Si PhaseDB lo devuelve envuelto (Ej: {"PRE-6A22A6DC": { ... }})
            if (rootNode.has(idLimpio)) {
                return parsearPresupuesto(rootNode.get(idLimpio));
            } 
            // Caso 2: Si PhaseDB devuelve el objeto directamente (Ej: {"id": "PRE-6A22A6DC", ...})
            else if (rootNode.has("id")) {
                return parsearPresupuesto(rootNode);
            }
            
            return null; // Si no tiene el formato esperado, devolvemos null (404)
        } catch (Exception e) {
            System.err.println("Error en obtenerPorId: " + e.getMessage());
            return null;
        }
    }
    // MÉTODO AYUDANTE: Transforma el formato de PhaseDB a Java
    private PresupuestoDTO parsearPresupuesto(com.fasterxml.jackson.databind.JsonNode node) {
        PresupuestoDTO p = new PresupuestoDTO();
        p.setId(node.path("id").asText(null));
        p.setRutCliente(node.path("rutCliente").asText(null));
        p.setFechaEmision(node.path("fechaEmision").asText(null));
        p.setSubtotal(node.path("subtotal").asDouble(0));
        p.setImpuestos(node.path("impuestos").asDouble(0));
        p.setTotal(node.path("total").asDouble(0));
        p.setEstado(node.path("estado").asText("BORRADOR"));

        List<PresupuestoDTO.ItemPresupuestoDTO> listaItems = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode itemsNode = node.path("items");
        
        // Magia aquí: Si PhaseDB lo devuelve como Objeto {"1":{}, "2":{}}, sacamos solo los valores
        if (itemsNode.isObject()) {
            itemsNode.elements().forEachRemaining(itemNode -> {
                try {
                    listaItems.add(objectMapper.treeToValue(itemNode, PresupuestoDTO.ItemPresupuestoDTO.class));
                } catch (Exception e) {}
            });
        } 
        // Por seguridad: Si llega a venir como Arreglo normal [{...}]
        else if (itemsNode.isArray()) {
            itemsNode.forEach(itemNode -> {
                try {
                    listaItems.add(objectMapper.treeToValue(itemNode, PresupuestoDTO.ItemPresupuestoDTO.class));
                } catch (Exception e) {}
            });
        }
        
        p.setItems(listaItems);
        return p;
    }

    public void actualizarEstado(String id, String nuevoEstado) throws Exception {
        // 1. Verificamos que el presupuesto exista usando el método GET que ya corregimos
        PresupuestoDTO existente = obtenerPorId(id);
        if (existente == null) {
            throw new RuntimeException("PRESUPUESTO_NO_ENCONTRADO");
        }

        // 2. Usamos ZMERGE para inyectar solo la actualización del estado.
        // Esto actuará como un verdadero PATCH, dejando los ítems, rut y totales intactos.
        String comando = String.format("ZMERGE ^PRESUPUESTOS(\"%s\")={\"estado\":\"%s\"}", id, nuevoEstado);
        phaseDbClient.ejecutarComando(comando);
    }

}