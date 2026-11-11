package com.phase.api.phase;

/** Operacion de escritura incluida en un comando Phase multi-linea. */
public record WriteOp(String global, String key, String json, boolean replace) {

    public static WriteOp replace(String global, String key, String json) {
        return new WriteOp(global, key, json, true);
    }

    public static WriteOp merge(String global, String key, String json) {
        return new WriteOp(global, key, json, false);
    }
}
