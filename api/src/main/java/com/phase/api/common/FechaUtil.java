package com.phase.api.common;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Parseo y validacion estrictos de fechas.
 *
 * <p>Antes los servicios caian silenciosamente en {@code LocalDate.now()} cuando
 * la fecha venia mal formada. Ahora cualquier fecha invalida se rechaza con
 * {@code 422 FECHA_INVALIDA} para no corromper vencimientos ni cartera.</p>
 */
public final class FechaUtil {

    private static final LocalDate MIN = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX = LocalDate.of(2100, 12, 31);

    private FechaUtil() {
    }

    /** Fecha obligatoria en formato {@code yyyy-MM-dd}. */
    public static LocalDate requerida(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw invalida(campo, "La fecha es obligatoria");
        }
        return parse(valor, campo);
    }

    /** Fecha opcional: si viene vacia se usa {@code defecto}. */
    public static LocalDate opcional(String valor, String campo, LocalDate defecto) {
        if (valor == null || valor.isBlank()) {
            return defecto;
        }
        return parse(valor, campo);
    }

    /** Valida el formato de una fecha opcional sin transformarla. */
    public static void validarOpcional(String valor, String campo) {
        if (valor != null && !valor.isBlank()) {
            parse(valor, campo);
        }
    }

    private static LocalDate parse(String valor, String campo) {
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw invalida(campo, "Formato esperado YYYY-MM-DD");
        }
        if (fecha.isBefore(MIN) || fecha.isAfter(MAX)) {
            throw invalida(campo, "La fecha debe estar entre 2000-01-01 y 2100-12-31");
        }
        return fecha;
    }

    private static ApiException invalida(String campo, String detalle) {
        return ApiException.unprocessable(
                "FECHA_INVALIDA",
                "La fecha indicada no es valida",
                Map.of(campo == null ? "fecha" : campo, detalle));
    }
}
