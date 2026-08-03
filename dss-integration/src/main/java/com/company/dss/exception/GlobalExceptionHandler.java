package com.company.dss.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DssAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(DssAuthenticationException ex) {
        log.warn("Erreur d'authentification DSS : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("DSS_AUTHENTICATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(DssClientException.class)
    public ResponseEntity<Map<String, Object>> handleDssClient(DssClientException ex) {
        log.error("Erreur client DSS : {}", ex.getMessage());
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        if (ex.getStatusCode() == 401) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (ex.getStatusCode() == 404) {
            status = HttpStatus.NOT_FOUND;
        }
        return ResponseEntity.status(status).body(errorBody("DSS_CLIENT_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Requête invalide : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Configuration invalide : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(errorBody("CONFIGURATION_ERROR", ex.getMessage()));
    }

    private Map<String, Object> errorBody(String code, String message) {
        return Map.of(
                "error", code,
                "message", message,
                "timestamp", Instant.now().toString()
        );
    }
}
