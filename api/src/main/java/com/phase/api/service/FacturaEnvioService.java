package com.phase.api.service;

import com.phase.api.common.ApiException;
import com.phase.api.common.SequenceService;
import com.phase.api.dto.ClienteDTO;
import com.phase.api.dto.EnviarFacturaRequest;
import com.phase.api.dto.EnvioFacturaDTO;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Envio de facturas por correo simulado. El "envio" se guarda en el global
 * ENVIOS para que el frontend pueda mostrar el historial, pero no sale de la
 * maquina: es parte del alcance demo.
 */
@Service
public class FacturaEnvioService {

    public static final String GLOBAL = "ENVIOS";

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final SequenceService sequenceService;
    private final FacturaService facturaService;
    private final ClienteService clienteService;

    public FacturaEnvioService(PhaseClient phaseClient,
                               ObjectMapper objectMapper,
                               SequenceService sequenceService,
                               FacturaService facturaService,
                               ClienteService clienteService) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.sequenceService = sequenceService;
        this.facturaService = facturaService;
        this.clienteService = clienteService;
    }

    public EnvioFacturaDTO enviar(String facturaId, EnviarFacturaRequest request) {
        FacturaDTO factura = facturaService.obtenerPorId(facturaId);
        ClienteDTO cliente = clienteDe(factura.getRutCliente());

        String destinatario = request != null && request.destinatario() != null
                && !request.destinatario().isBlank()
                ? request.destinatario().trim()
                : cliente == null ? null : cliente.getEmail();
        if (destinatario == null || destinatario.isBlank()) {
            throw ApiException.badRequest("DESTINATARIO_REQUERIDO",
                    "El cliente no tiene email registrado; indica un destinatario");
        }

        String asunto = request != null && request.asunto() != null && !request.asunto().isBlank()
                ? request.asunto().trim()
                : "Factura " + factura.getId() + " - ERP Empresarial";
        String mensaje = request != null && request.mensaje() != null && !request.mensaje().isBlank()
                ? request.mensaje().trim()
                : mensajeSugerido(factura, cliente);

        EnvioFacturaDTO envio = new EnvioFacturaDTO(
                sequenceService.nextId("ENV"),
                factura.getId(),
                destinatario,
                asunto,
                mensaje,
                LocalDate.now().toString(),
                "SIMULADO",
                List.of(factura.getId() + ".pdf")
        );
        phaseClient.writeJson(GLOBAL, envio.id(), PhaseJson.toJson(envio, objectMapper), true);
        return envio;
    }

    public List<EnvioFacturaDTO> listarPorFactura(String facturaId) {
        Map<String, EnvioFacturaDTO> envios =
                PhaseJson.toMap(phaseClient.readAll(GLOBAL), EnvioFacturaDTO.class, objectMapper);
        String key = facturaId == null ? "" : facturaId.trim();
        List<EnvioFacturaDTO> result = new ArrayList<>();
        for (EnvioFacturaDTO envio : envios.values()) {
            if (key.isEmpty() || key.equalsIgnoreCase(envio.facturaId())) {
                result.add(envio);
            }
        }
        result.sort(Comparator.comparing(EnvioFacturaDTO::fechaEnvio,
                Comparator.nullsLast(String::compareTo)).reversed());
        return result;
    }

    private String mensajeSugerido(FacturaDTO factura, ClienteDTO cliente) {
        String nombre = cliente == null ? "cliente" : cliente.getNombre();
        return "Estimado equipo de " + nombre + ":\n\n"
                + "Adjuntamos la factura " + factura.getId() + " por un total de "
                + formatoMoneda(factura.getTotal()) + ", con vencimiento "
                + factura.getFechaVencimiento() + ".\n\n"
                + "Quedamos atentos a cualquier consulta.\n\n"
                + "Departamento de Finanzas.";
    }

    private String formatoMoneda(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(safe);
    }

    private ClienteDTO clienteDe(String rut) {
        try {
            return clienteService.obtener(rut);
        } catch (ApiException e) {
            return null;
        }
    }
}
