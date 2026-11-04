package com.phase.api.service;

import com.phase.api.common.ApiException;
import com.phase.api.common.SequenceService;
import com.phase.api.dto.CuentaPorCobrarDTO;
import com.phase.api.dto.FacturaDTO;
import com.phase.api.dto.PromesaPagoDTO;
import com.phase.api.dto.RecordatorioDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CobranzaService {

    public static final String GLOBAL_RECORDATORIOS = "COBRANZA";
    public static final String GLOBAL_PROMESAS = "PROMESAS";

    private static final Set<String> CANALES = Set.of("EMAIL", "WHATSAPP");
    private static final Set<String> ESTADOS_PROMESA = Set.of("PENDIENTE", "CUMPLIDA", "INCUMPLIDA");

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final SequenceService sequenceService;
    private final FacturaService facturaService;
    private final CarteraService carteraService;
    private final ClienteService clienteService;

    public CobranzaService(PhaseClient phaseClient,
                           ObjectMapper objectMapper,
                           SequenceService sequenceService,
                           FacturaService facturaService,
                           CarteraService carteraService,
                           ClienteService clienteService) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.sequenceService = sequenceService;
        this.facturaService = facturaService;
        this.carteraService = carteraService;
        this.clienteService = clienteService;
    }

    public RecordatorioDTO enviarRecordatorio(RecordatorioDTO dto) {
        FacturaDTO factura = facturaService.obtenerPorId(dto.facturaId());
        CuentaPorCobrarDTO cuenta = carteraService.obtener(factura.getId());

        String canal = normalizarCanal(dto.canal());
        String cliente = cuenta.cliente() == null ? factura.getRutCliente() : cuenta.cliente();
        String asunto = dto.asunto() == null || dto.asunto().isBlank()
                ? "Recordatorio de pago - Factura " + factura.getId()
                : dto.asunto().trim();
        String mensaje = dto.mensaje() == null || dto.mensaje().isBlank()
                ? mensajeSugerido(cliente, factura, cuenta)
                : dto.mensaje().trim();

        RecordatorioDTO recordatorio = new RecordatorioDTO(
                sequenceService.nextId("REC"),
                factura.getId(),
                factura.getRutCliente(),
                cliente,
                canal,
                asunto,
                mensaje,
                LocalDate.now().toString(),
                "SIMULADO"
        );
        phaseClient.writeJson(GLOBAL_RECORDATORIOS, recordatorio.id(),
                PhaseJson.toJson(recordatorio, objectMapper), true);
        return recordatorio;
    }

    public List<RecordatorioDTO> enviarRecordatoriosMasivos() {
        List<RecordatorioDTO> enviados = new ArrayList<>();
        for (CuentaPorCobrarDTO cuenta : carteraService.listar()) {
            if (!"PAGADA".equals(cuenta.estado()) && cuenta.montoPendiente().signum() > 0) {
                enviados.add(enviarRecordatorio(new RecordatorioDTO(
                        null, cuenta.facturaId(), cuenta.rutCliente(), cuenta.cliente(),
                        "EMAIL", null, null, null, null)));
            }
        }
        return enviados;
    }

    public List<RecordatorioDTO> listarRecordatorios(String facturaId) {
        Map<String, RecordatorioDTO> recordatorios =
                PhaseJson.toMap(phaseClient.readAll(GLOBAL_RECORDATORIOS), RecordatorioDTO.class, objectMapper);
        String key = facturaId == null ? "" : facturaId.trim();
        return recordatorios.values().stream()
                .filter(rec -> key.isEmpty() || key.equalsIgnoreCase(rec.facturaId()))
                .sorted(Comparator.comparing(RecordatorioDTO::fechaEnvio,
                        Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
    }

    public PromesaPagoDTO registrarPromesa(PromesaPagoDTO dto) {
        FacturaDTO factura = facturaService.obtenerPorId(dto.facturaId());
        if ("ANULADA".equalsIgnoreCase(factura.getEstado())) {
            throw ApiException.conflict("FACTURA_ANULADA", "No se puede prometer pago de una factura anulada");
        }

        PromesaPagoDTO promesa = new PromesaPagoDTO(
                sequenceService.nextId("PROM"),
                factura.getId(),
                factura.getRutCliente(),
                clienteService.nombreDe(factura.getRutCliente()),
                parseFecha(dto.fechaPromesa()).toString(),
                dto.monto(),
                dto.nota() == null ? null : dto.nota().trim(),
                LocalDate.now().toString(),
                "PENDIENTE"
        );
        phaseClient.writeJson(GLOBAL_PROMESAS, promesa.id(),
                PhaseJson.toJson(promesa, objectMapper), true);
        return promesa;
    }

    public List<PromesaPagoDTO> listarPromesas(String facturaId) {
        Map<String, PromesaPagoDTO> promesas =
                PhaseJson.toMap(phaseClient.readAll(GLOBAL_PROMESAS), PromesaPagoDTO.class, objectMapper);
        String key = facturaId == null ? "" : facturaId.trim();
        return promesas.values().stream()
                .filter(promesa -> key.isEmpty() || key.equalsIgnoreCase(promesa.facturaId()))
                .sorted(Comparator.comparing(PromesaPagoDTO::fechaRegistro,
                        Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
    }

    public PromesaPagoDTO actualizarEstadoPromesa(String id, String estado) {
        String json = phaseClient.read(GLOBAL_PROMESAS, id);
        PromesaPagoDTO promesa = PhaseJson.toObject(json, PromesaPagoDTO.class, objectMapper);
        if (promesa == null) {
            throw ApiException.notFound("Promesa de pago no encontrada: " + id);
        }
        String normalized = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS_PROMESA.contains(normalized)) {
            throw ApiException.badRequest("ESTADO_INVALIDO",
                    "Estado de promesa invalido. Valores permitidos: " + ESTADOS_PROMESA);
        }
        PromesaPagoDTO actualizada = new PromesaPagoDTO(
                promesa.id(), promesa.facturaId(), promesa.rutCliente(), promesa.cliente(),
                promesa.fechaPromesa(), promesa.monto(), promesa.nota(),
                promesa.fechaRegistro(), normalized);
        phaseClient.writeJson(GLOBAL_PROMESAS, id, PhaseJson.toJson(actualizada, objectMapper), true);
        return actualizada;
    }

    private String mensajeSugerido(String cliente, FacturaDTO factura, CuentaPorCobrarDTO cuenta) {
        BigDecimal pendiente = cuenta.montoPendiente();
        String atraso = cuenta.diasAtraso() > 0
                ? "presenta un atraso de " + cuenta.diasAtraso() + " dias"
                : "esta proxima a vencer";
        return "Estimado equipo de " + cliente + ":\n\n"
                + "Le informamos que su factura " + factura.getId() + " " + atraso
                + ". El saldo pendiente es de " + pendiente + " "
                + cuenta.estado() + ".\n\n"
                + "Si el pago ya fue realizado, por favor envie el comprobante respondiendo a este mensaje.\n\n"
                + "Departamento de Finanzas.";
    }

    private String normalizarCanal(String canal) {
        String normalized = canal == null || canal.isBlank()
                ? "EMAIL"
                : canal.trim().toUpperCase(Locale.ROOT);
        if (!CANALES.contains(normalized)) {
            throw ApiException.badRequest("CANAL_INVALIDO",
                    "Canal invalido. Valores permitidos: " + CANALES);
        }
        return normalized;
    }

    private LocalDate parseFecha(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw ApiException.badRequest("FECHA_INVALIDA", "La fecha debe tener formato YYYY-MM-DD");
        }
    }
}
