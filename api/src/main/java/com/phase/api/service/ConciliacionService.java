package com.phase.api.service;

import com.phase.api.common.ApiException;
import com.phase.api.common.SequenceService;
import com.phase.api.config.FinanceProperties;
import com.phase.api.dto.CuentaPorCobrarDTO;
import com.phase.api.dto.MovimientoConciliacionDTO;
import com.phase.api.dto.ResumenConciliacionDTO;
import com.phase.api.phase.PhaseClient;
import com.phase.api.phase.PhaseJson;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Conciliacion bancaria simulada: no se importa CSV ni se conecta al banco.
 * El boton "Importar cartola" se representa con {@link #simular()} y el cruce
 * automatico con {@link #ejecutar()}.
 */
@Service
public class ConciliacionService {

    public static final String GLOBAL = "CONCILIACION";

    private final PhaseClient phaseClient;
    private final ObjectMapper objectMapper;
    private final SequenceService sequenceService;
    private final CarteraService carteraService;
    private final FinanceProperties financeProperties;

    public ConciliacionService(PhaseClient phaseClient,
                               ObjectMapper objectMapper,
                               SequenceService sequenceService,
                               CarteraService carteraService,
                               FinanceProperties financeProperties) {
        this.phaseClient = phaseClient;
        this.objectMapper = objectMapper;
        this.sequenceService = sequenceService;
        this.carteraService = carteraService;
        this.financeProperties = financeProperties;
    }

    public ResumenConciliacionDTO resumen() {
        List<MovimientoConciliacionDTO> movimientos = listar();

        BigDecimal saldo = BigDecimal.ZERO;
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal egresos = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        long pendientes = 0;
        long conciliados = 0;

        for (MovimientoConciliacionDTO movimiento : movimientos) {
            BigDecimal monto = movimiento.monto() == null ? BigDecimal.ZERO : movimiento.monto();
            saldo = saldo.add(monto);
            if (monto.signum() >= 0) {
                ingresos = ingresos.add(monto);
            } else {
                egresos = egresos.add(monto.abs());
            }
            if ("CONCILIADO".equalsIgnoreCase(movimiento.estado())) {
                conciliados++;
            } else {
                pendientes++;
                pendiente = pendiente.add(monto.abs());
            }
        }

        return new ResumenConciliacionDTO(
                saldo.setScale(2, RoundingMode.HALF_UP),
                ingresos.setScale(2, RoundingMode.HALF_UP),
                egresos.setScale(2, RoundingMode.HALF_UP),
                pendientes,
                conciliados,
                pendiente.setScale(2, RoundingMode.HALF_UP),
                financeProperties.getCurrency(),
                movimientos
        );
    }

    public ResumenConciliacionDTO simular() {
        if (!listar().isEmpty()) {
            return resumen();
        }

        List<CuentaPorCobrarDTO> cartera = carteraService.listar();
        cartera.stream()
                .filter(cuenta -> cuenta.montoPendiente().signum() > 0)
                .min(Comparator.comparing(CuentaPorCobrarDTO::fechaVencimiento,
                        Comparator.nullsLast(String::compareTo)))
                .ifPresent(cuenta -> crear(new MovimientoConciliacionDTO(
                        null,
                        LocalDate.now().toString(),
                        "TRF. " + cuenta.cliente().toUpperCase(Locale.ROOT),
                        cuenta.montoPendiente(),
                        cuenta.facturaId(),
                        "CONCILIADO",
                        "SIMULADO"
                )));

        crear(new MovimientoConciliacionDTO(
                null,
                LocalDate.now().toString(),
                "MANTENIMIENTO CUENTA",
                new BigDecimal("-45.00"),
                "Gasto Bancario Reg.",
                "CONCILIADO",
                "SIMULADO"
        ));

        crear(new MovimientoConciliacionDTO(
                null,
                LocalDate.now().toString(),
                "PAGO PROVEEDOR SUR",
                new BigDecimal("-3200.00"),
                null,
                "PENDIENTE",
                "SIMULADO"
        ));

        return resumen();
    }

    public ResumenConciliacionDTO ejecutar() {
        List<MovimientoConciliacionDTO> movimientos = listar();
        List<CuentaPorCobrarDTO> cartera = carteraService.listar();
        Set<String> usadas = new HashSet<>();

        for (MovimientoConciliacionDTO movimiento : movimientos) {
            if ("CONCILIADO".equalsIgnoreCase(movimiento.estado())
                    || movimiento.monto() == null
                    || movimiento.monto().signum() <= 0) {
                continue;
            }
            cartera.stream()
                    .filter(cuenta -> cuenta.montoPendiente().signum() > 0)
                    .filter(cuenta -> !usadas.contains(cuenta.facturaId()))
                    .filter(cuenta -> cuenta.montoPendiente()
                            .subtract(movimiento.monto()).abs()
                            .compareTo(new BigDecimal("0.01")) <= 0)
                    .findFirst()
                    .ifPresent(cuenta -> {
                        usadas.add(cuenta.facturaId());
                        conciliar(movimiento.id(), cuenta.facturaId());
                    });
        }

        return resumen();
    }

    public MovimientoConciliacionDTO conciliar(String id, String documentoErp) {
        MovimientoConciliacionDTO movimiento = obtener(id);
        if ("CONCILIADO".equalsIgnoreCase(movimiento.estado())) {
            throw ApiException.conflict("YA_CONCILIADO", "El movimiento ya esta conciliado");
        }
        String documento = documentoErp == null || documentoErp.isBlank()
                ? movimiento.documentoErp()
                : documentoErp.trim();
        if (documento == null || documento.isBlank()) {
            throw ApiException.badRequest("DOCUMENTO_REQUERIDO",
                    "Debes indicar el documento ERP a vincular");
        }

        MovimientoConciliacionDTO conciliado = new MovimientoConciliacionDTO(
                movimiento.id(), movimiento.fecha(), movimiento.concepto(), movimiento.monto(),
                documento, "CONCILIADO", movimiento.origen());
        phaseClient.writeJson(GLOBAL, id, PhaseJson.toJson(conciliado, objectMapper), true);
        return conciliado;
    }

    public MovimientoConciliacionDTO obtener(String id) {
        MovimientoConciliacionDTO movimiento =
                PhaseJson.toObject(phaseClient.read(GLOBAL, id), MovimientoConciliacionDTO.class, objectMapper);
        if (movimiento == null) {
            throw ApiException.notFound("Movimiento no encontrado: " + id);
        }
        return movimiento;
    }

    public List<MovimientoConciliacionDTO> listar() {
        Map<String, MovimientoConciliacionDTO> movimientos =
                PhaseJson.toMap(phaseClient.readAll(GLOBAL), MovimientoConciliacionDTO.class, objectMapper);
        List<MovimientoConciliacionDTO> result = new ArrayList<>(movimientos.values());
        result.sort(Comparator.comparing(MovimientoConciliacionDTO::fecha,
                Comparator.nullsLast(String::compareTo)).reversed());
        return result;
    }

    private MovimientoConciliacionDTO crear(MovimientoConciliacionDTO movimiento) {
        MovimientoConciliacionDTO creado = new MovimientoConciliacionDTO(
                sequenceService.nextId("MOV"),
                movimiento.fecha(),
                movimiento.concepto(),
                movimiento.monto().setScale(2, RoundingMode.HALF_UP),
                movimiento.documentoErp(),
                movimiento.estado(),
                movimiento.origen()
        );
        phaseClient.writeJson(GLOBAL, creado.id(), PhaseJson.toJson(creado, objectMapper), true);
        return creado;
    }
}
