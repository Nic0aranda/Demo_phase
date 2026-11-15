package com.phase.api.phase;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Construye comandos Phase de forma centralizada.
 *
 * <p>Todos los valores que vienen del exterior pasan por {@link #quote(String)}.
 * En Phase una cadena se delimita con comillas dobles y una comilla literal se
 * representa duplicandola. Tambien se valida el nombre del global porque esta
 * build no admite guiones bajos ni puntos.</p>
 */
public final class PhaseCommands {

    private static final Pattern GLOBAL_NAME = Pattern.compile("[A-Za-z%][A-Za-z0-9]*");

    private PhaseCommands() {
    }

    public static String globalName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Nombre de global Phase invalido: null");
        }
        String clean = name.startsWith("^") ? name.substring(1) : name;
        if (!GLOBAL_NAME.matcher(clean).matches()) {
            throw new IllegalArgumentException("Nombre de global Phase invalido: " + name);
        }
        return clean;
    }

    public static String global(String name) {
        return "^" + globalName(name);
    }

    public static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String subscript(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El subscript no puede estar vacio");
        }
        return quote(value);
    }

    public static String readJson(String global, String key) {
        return "ZWRITE " + global(global) + "(" + subscript(key) + ")";
    }

    public static String readJsonAt(String global, String key, long transaction) {
        if (transaction < 0) {
            throw new IllegalArgumentException("La transaccion debe ser un numero absoluto no negativo");
        }
        String target = global(global);
        return "DO WITH @TX(" + transaction + ") @BIND(" + target + ")\n. ZWRITE "
                + target + "(" + subscript(key) + ")";
    }

    public static String latestChanged(String global, String key, long transaction) {
        if (transaction < 0) {
            throw new IllegalArgumentException("La transaccion debe ser un numero absoluto no negativo");
        }
        return "WRITE $CHANGED(^[" + transaction + "]" + globalName(global) + "(" + subscript(key) + ")),!";
    }

    public static String currentTransaction() {
        return "WRITE $TX,!";
    }

    public static String transactionTimestamp(long transaction) {
        if (transaction < 0) {
            throw new IllegalArgumentException("La transaccion debe ser un numero absoluto no negativo");
        }
        return "WRITE $WHEN(" + transaction + "),!";
    }

    public static String readAll(String global) {
        return "ZWRITE " + global(global);
    }

    public static String exists(String global, String key) {
        return "WRITE $DATA(" + global(global) + "(" + subscript(key) + ")),!";
    }

    public static String kill(String global, String key) {
        return "KILL " + global(global) + "(" + subscript(key) + ")";
    }

    /**
     * Escribe un JSON completo. Si {@code replace} es true hace KILL + ZMERGE en
     * el mismo request, lo que reemplaza la rama sin dejar hijos viejos.
     */
    public static String writeJson(String global, String key, String json, boolean replace) {
        String target = global(global) + "(" + subscript(key) + ")";
        StringBuilder command = new StringBuilder();
        command.append("SET J=").append(quote(json)).append('\n');
        if (replace) {
            command.append("KILL ").append(target).append('\n');
        }
        command.append("ZMERGE ").append(target).append("=J");
        return command.toString();
    }

    /**
     * Actualiza solo las claves presentes en el JSON, sin borrar el resto.
     */
    public static String writePartialJson(String global, String key, String json) {
        return writeJson(global, key, json, false);
    }

    /**
     * Agrupa varias escrituras en un solo request a PhaseDB. Reduce la ventana
     * en la que dos globales relacionados (por ejemplo pago y factura) pueden
     * quedar inconsistentes.
     */
    public static String writeJsonBatch(List<WriteOp> operations) {
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("El batch de escritura no puede estar vacio");
        }
        StringBuilder command = new StringBuilder();
        int index = 0;
        for (WriteOp operation : operations) {
            index++;
            String variable = "J" + index;
            String target = global(operation.global()) + "(" + subscript(operation.key()) + ")";
            command.append("SET ").append(variable).append('=').append(quote(operation.json())).append('\n');
            if (operation.replace()) {
                command.append("KILL ").append(target).append('\n');
            }
            command.append("ZMERGE ").append(target).append('=').append(variable).append('\n');
        }
        return command.toString().trim();
    }

    public static String setScalar(String global, String key, String value) {
        return "SET " + global(global) + "(" + subscript(key) + ")=" + quote(value);
    }
}
