package com.phase.api.phase;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduce entre el JSON que devuelve Phase y los DTO Java.
 *
 * <p>ZMERGE guarda los arrays como objetos con claves "1", "2", ...; este
 * helper los normaliza a arrays antes de deserializar.</p>
 */
public final class PhaseJson {

    private PhaseJson() {
    }

    public static String toJson(Object value, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new PhaseException("JSON_SERIALIZE", "No se pudo serializar el objeto: " + e.getMessage());
        }
    }

    public static <T> T toObject(String json, Class<T> type, ObjectMapper mapper) {
        if (json == null || json.isBlank() || "null".equals(json.trim())) {
            return null;
        }
        try {
            JsonNode normalized = normalize(mapper.readTree(json));
            return mapper.treeToValue(normalized, type);
        } catch (JacksonException e) {
            throw new PhaseException("JSON_PARSE", "No se pudo leer el JSON de Phase: " + e.getMessage());
        }
    }

    public static <T> Map<String, T> toMap(String json, Class<T> type, ObjectMapper mapper) {
        Map<String, T> result = new LinkedHashMap<>();
        if (json == null || json.isBlank() || "null".equals(json.trim())) {
            return result;
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isObject()) {
                return result;
            }
            for (Map.Entry<String, JsonNode> entry : root.properties()) {
                if (!entry.getValue().isObject()) {
                    continue;
                }
                try {
                    result.put(entry.getKey(), mapper.treeToValue(normalize(entry.getValue()), type));
                } catch (JacksonException e) {
                    throw new PhaseException("JSON_PARSE",
                            "No se pudo leer el nodo " + entry.getKey() + ": " + e.getMessage());
                }
            }
            return result;
        } catch (JacksonException e) {
            throw new PhaseException("JSON_PARSE", "No se pudo leer el JSON de Phase: " + e.getMessage());
        }
    }

    /**
     * Convierte recursivamente objetos con claves numericas ("1", "2", ...) en
     * arrays ordenados. El resto de objetos se conserva.
     */
    public static JsonNode normalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> array.add(normalize(child)));
            return array;
        }
        if (node.isObject()) {
            List<String> names = new ArrayList<>(node.propertyNames());

            boolean numericObject = !names.isEmpty() && names.stream().allMatch(name -> name.matches("\\d+"));
            if (numericObject) {
                names.sort(Comparator.comparingInt(Integer::parseInt));
                ArrayNode array = JsonNodeFactory.instance.arrayNode();
                for (String name : names) {
                    array.add(normalize(node.get(name)));
                }
                return array;
            }

            ObjectNode object = JsonNodeFactory.instance.objectNode();
            for (String name : names) {
                object.set(name, normalize(node.get(name)));
            }
            return object;
        }
        return node;
    }
}
