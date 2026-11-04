package com.phase.api.common;

import java.time.Instant;
import java.util.Map;

public class ApiError {

    private Instant timestamp;
    private int status;
    private String code;
    private String message;
    private String path;
    private String requestId;
    private Map<String, String> details;

    public ApiError() {
    }

    public ApiError(Instant timestamp, int status, String code, String message,
                    String path, String requestId, Map<String, String> details) {
        this.timestamp = timestamp;
        this.status = status;
        this.code = code;
        this.message = message;
        this.path = path;
        this.requestId = requestId;
        this.details = details;
    }

    public static ApiError of(int status, String code, String message, String path,
                              String requestId, Map<String, String> details) {
        return new ApiError(Instant.now(), status, code, message, path, requestId, details);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }
}
