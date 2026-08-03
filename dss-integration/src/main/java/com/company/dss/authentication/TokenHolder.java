package com.company.dss.authentication;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Stockage thread-safe du token DSS (X-Subject-Token) de la session courante.
 */
@Slf4j
@Component
public class TokenHolder {

    private final AtomicReference<TokenSession> session = new AtomicReference<>();

    public void setToken(String token, int durationMinutes) {
        Instant expiresAt = Instant.now().plusSeconds(Math.max(durationMinutes * 60L - 60L, 60L));
        session.set(new TokenSession(token, expiresAt));
        log.info("Token DSS enregistré, expiration prévue à {}", expiresAt);
    }

    public Optional<String> getToken() {
        TokenSession current = session.get();
        if (current == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(current.expiresAt())) {
            clear();
            return Optional.empty();
        }
        return Optional.of(current.token());
    }

    public boolean hasValidToken() {
        return getToken().isPresent();
    }

    public void clear() {
        session.set(null);
        log.info("Token DSS supprimé");
    }

    private record TokenSession(String token, Instant expiresAt) {
    }
}
