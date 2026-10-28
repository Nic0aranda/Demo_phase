package com.phase.api.service;

import com.phase.api.config.PhaseDbClient;
import com.phase.api.dto.FacturaDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FacturaService {

    private final PhaseDbClient phaseDbClient;
    private final ObjectMapper objectMapper;

    public FacturaService(PhaseDbClient phaseDbClient) {
        this.phaseDbClient = phaseDbClient;
        this.objectMapper = new ObjectMapper();
    }

    public FacturaDTO emitirFactura(FacturaDTO factura) throws Exception {
        if (factura.getId() == null || factura.getId().isEmpty()) {
            factura.setId("FAC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        double subtotal = 0;
        for (FacturaDTO.ItemFacturaDTO item : factura.getItems()) {
            double totalLinea = item.getCantidad() * item.getPrecioUnitario();
            item.setTotalLinea(totalLinea);
            subtotal += totalLinea;
        }
        
        factura.setSubtotal(subtotal);
        factura.setImpuestos(subtotal * 0.19);
        factura.setTotal(subtotal + factura.getImpuestos());

        String jsonPayload = objectMapper.writeValueAsString(factura);
        String comando = String.format("ZMERGE ^FACTURAS(\"%s\")=%s", factura.getId(), jsonPayload);
        phaseDbClient.ejecutarComando(comando);

        return factura;
    }

    public List<FacturaDTO> listarTodas() {
        List<FacturaDTO> facturas = new ArrayList<>();
        try {
            String comando = "ZWRITE ^FACTURAS";
            String respuesta = phaseDbClient.ejecutarComando(comando);
            if (respuesta == null || respuesta.trim().isEmpty() || respuesta.equals("null")) return facturas;

            var rootNode = objectMapper.readTree(respuesta);
            if (rootNode.isObject()) {
                rootNode.fields().forEachRemaining(entry -> {
                    var node = entry.getValue();
                    if (node.isObject() && node.has("id")) facturas.add(parsearFactura(node));
                });
            }
        } catch (Exception e) {}
        return facturas;
    }

    public FacturaDTO obtenerPorId(String id) {
        String idLimpio = id.replace("\"", "").trim();
        try {
            String comando = String.format("ZWRITE ^FACTURAS(\"%s\")", idLimpio);
            String respuesta = phaseDbClient.ejecutarComando(comando);
            if (respuesta == null || respuesta.trim().isEmpty() || respuesta.equals("null")) return null;

            var rootNode = objectMapper.readTree(respuesta);
            if (rootNode.has(idLimpio)) return parsearFactura(rootNode.get(idLimpio));
            else if (rootNode.has("id")) return parsearFactura(rootNode);
            
            return null;
        } catch (Exception e) { return null; }
    }

    public void actualizarEstado(String id, String nuevoEstado) throws Exception {
        String idLimpio = id.replace("\"", "").trim();
        if (obtenerPorId(idLimpio) == null) throw new RuntimeException("FACTURA_NO_ENCONTRADA");
        String comando = String.format("ZMERGE ^FACTURAS(\"%s\")={\"estado\":\"%s\"}", idLimpio, nuevoEstado);
        phaseDbClient.ejecutarComando(comando);
    }

    // Adaptador de diccionarios PhaseDB a Listas Java
    private FacturaDTO parsearFactura(com.fasterxml.jackson.databind.JsonNode node) {
        FacturaDTO f = new FacturaDTO();
        f.setId(node.path("id").asText(null));
        f.setRutCliente(node.path("rutCliente").asText(null));
        f.setIdOperacionOrigen(node.path("idOperacionOrigen").asText(null));
        f.setFechaEmision(node.path("fechaEmision").asText(null));
        f.setSubtotal(node.path("subtotal").asDouble(0));
        f.setImpuestos(node.path("impuestos").asDouble(0));
        f.setTotal(node.path("total").asDouble(0));
        f.setEstado(node.path("estado").asText("PENDIENTE"));

        List<FacturaDTO.ItemFacturaDTO> listaItems = new ArrayList<>();
        var itemsNode = node.path("items");
        
        if (itemsNode.isObject()) {
            itemsNode.elements().forEachRemaining(itemNode -> {
                try { listaItems.add(objectMapper.treeToValue(itemNode, FacturaDTO.ItemFacturaDTO.class)); } 
                catch (Exception e) {}
            });
        } else if (itemsNode.isArray()) {
            itemsNode.forEach(itemNode -> {
                try { listaItems.add(objectMapper.treeToValue(itemNode, FacturaDTO.ItemFacturaDTO.class)); } 
                catch (Exception e) {}
            });
        }
        
        f.setItems(listaItems);
        return f;
    }
}