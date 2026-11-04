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

    void kill(String global, String key);
}
