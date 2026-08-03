package com.company.dss.exception;

public class DssClientException extends RuntimeException {

    private final int statusCode;

    public DssClientException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public DssClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public DssClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
