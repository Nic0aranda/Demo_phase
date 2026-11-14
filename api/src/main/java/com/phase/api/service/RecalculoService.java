package com.phase.api.service;

import com.phase.api.dto.FacturaDTO;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Repara estados de factura que hayan quedado desalineados por un fallo entre
 * la escritura del movimiento y la actualizacion del estado.
 */
@Service
public class RecalculoService {

    private final FacturaService facturaService;
    private final FacturaEstadoService facturaEstadoService;

    public RecalculoService(FacturaService facturaService,
                            FacturaEstadoService facturaEstadoService) {
        this.facturaService = facturaService;
        this.facturaEstadoService = facturaEstadoService;
    }

    public Map<String, Object> recalcularTodas() {
        List<FacturaDTO> facturas = facturaService.listarTodas();
        int corregidas = 0;

        for (FacturaDTO factura : facturas) {
            String antes = factura.getEstado();
            facturaEstadoService.recalcular(factura.getId());
            String despues = facturaService.obtenerPorId(factura.getId()).getEstado();
            if (!Objects.equals(antes, despues)) {
                corregidas++;
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("facturas", facturas.size());
        resultado.put("corregidas", corregidas);
        return resultado;
    }
}
