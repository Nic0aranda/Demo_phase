package com.phase.api.service;

import com.phase.api.config.PhaseDbClient;
import com.phase.api.dto.OperacionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OperacionService {

    private final PhaseDbClient phaseDbClient;
    private final ObjectMapper objectMapper;

    public OperacionService(PhaseDbClient phaseDbClient) {
        this.phaseDbClient = phaseDbClient;
        this.objectMapper = new ObjectMapper();
    }

    public OperacionDTO crearOperacion(OperacionDTO operacion) throws Exception {
        if (operacion.getId() == null || operacion.getId().isEmpty()) {
            operacion.setId("OP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        String jsonPayload = objectMapper.writeValueAsString(operacion);
        String comando = String.format("ZMERGE ^OPERACIONES(\"%s\")=%s", operacion.getId(), jsonPayload);
        phaseDbClient.ejecutarComando(comando);
        return operacion;
    }

    public List<OperacionDTO> listarTodas() {
        List<OperacionDTO> operaciones = new ArrayList<>();
        try {
            String comando = "ZWRITE ^OPERACIONES";
            String respuesta = phaseDbClient.ejecutarComando(comando);
            if (respuesta == null || respuesta.trim().isEmpty() || respuesta.equals("null")) return operaciones;

            var rootNode = objectMapper.readTree(respuesta);
            if (rootNode.isObject()) {
                rootNode.fields().forEachRemaining(entry -> {
                    var node = entry.getValue();
                    if (node.isObject() && node.has("id")) {
                        try { operaciones.add(objectMapper.treeToValue(node, OperacionDTO.class)); } 
                        catch (Exception e) {}
                    }
                });
            }
        } catch (Exception e) { System.err.println("Error listarOperaciones: " + e.getMessage()); }
        return operaciones;
    }

    public OperacionDTO obtenerPorId(String id) {
        String idLimpio = id.replace("\"", "").trim();
        try {
            String comando = String.format("ZWRITE ^OPERACIONES(\"%s\")", idLimpio);
            String respuesta = phaseDbClient.ejecutarComando(comando);
            if (respuesta == null || respuesta.trim().isEmpty() || respuesta.equals("null")) return null;

            var rootNode = objectMapper.readTree(respuesta);
            if (rootNode.has(idLimpio)) return objectMapper.treeToValue(rootNode.get(idLimpio), OperacionDTO.class);
            else if (rootNode.has("id")) return objectMapper.treeToValue(rootNode, OperacionDTO.class);
            
            return null;
        } catch (Exception e) { return null; }
    }

    public void actualizarEstado(String id, String nuevoEstado) throws Exception {
        String idLimpio = id.replace("\"", "").trim();
        if (obtenerPorId(idLimpio) == null) throw new RuntimeException("OPERACION_NO_ENCONTRADA");
        String comando = String.format("ZMERGE ^OPERACIONES(\"%s\")={\"estado\":\"%s\"}", idLimpio, nuevoEstado);
        phaseDbClient.ejecutarComando(comando);
    }
}