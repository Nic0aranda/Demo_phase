package com.phase.api.phase;

public class PhaseException extends RuntimeException {

    private final String code;
    private final String requestId;

    public PhaseException(String code, String message) {
        this(code, message, null);
    }

    public PhaseException(String code, String message, String requestId) {
        super(message);
        this.code = code;
        this.requestId = requestId;
    }

    public PhaseException(String code, String message, String requestId, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.requestId = requestId;
    }

    public String getCode() {
        return code;
    }

    public String getRequestId() {
        return requestId;
    }
}
