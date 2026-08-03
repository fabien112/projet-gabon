package com.company.dss.exception;

public class DssAuthenticationException extends DssClientException {

    public DssAuthenticationException(String message) {
        super(message);
    }

    public DssAuthenticationException(String message, int statusCode) {
        super(message, statusCode);
    }
}
