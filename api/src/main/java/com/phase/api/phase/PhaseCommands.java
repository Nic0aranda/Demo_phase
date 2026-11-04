package com.phase.api.phase;

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

    public static String global(String name) {
        if (name == null || !GLOBAL_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nombre de global Phase invalido: " + name);
        }
        return "^" + name;
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

    public static String setScalar(String global, String key, String value) {
        return "SET " + global(global) + "(" + subscript(key) + ")=" + quote(value);
    }
}
