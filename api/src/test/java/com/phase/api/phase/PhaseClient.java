package com.phase.api.phase;

public interface PhaseClient {

    PhaseResponse executeRaw(String command);

    String execute(String command);

    boolean health();

    boolean exists(String global, String key);

    String read(String global, String key);

    String readAll(String global);

    void writeJson(String global, String key, String json, boolean replace);

    void writePartialJson(String global, String key, String json);

    /**
     * Ejecuta varias escrituras en un solo comando Phase. La implementacion por
     * defecto usa {@link #execute(String)}, que ya envia el comando completo a
     * PhaseDB en una sola peticion HTTP.
     */
    default void writeJsonBatch(java.util.List<WriteOp> operations) {
        execute(PhaseCommands.writeJsonBatch(operations));
    }

    void kill(String global, String key);
}
