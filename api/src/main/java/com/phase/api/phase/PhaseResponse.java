package com.phase.api.phase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PhaseResponse {

    private boolean ok;
    private String output;
    private Object value;
    private PhaseError error;
    private long lastTx;
    private long elapsedMs;
    private String requestId;

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public PhaseError getError() {
        return error;
    }

    public void setError(PhaseError error) {
        this.error = error;
    }

    public long getLastTx() {
        return lastTx;
    }

    public void setLastTx(long lastTx) {
        this.lastTx = lastTx;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
