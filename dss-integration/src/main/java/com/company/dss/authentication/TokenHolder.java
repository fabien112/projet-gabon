package com.company.dss.authentication;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Stockage thread-safe de la session DSS (token, identité, clés AES pour MQ).
 */
@Slf4j
@Component
public class TokenHolder {

    private final AtomicReference<TokenSession> session = new AtomicReference<>();

    public void setSession(
            String token,
            int durationMinutes,
            String userId,
            String userGroupId,
            String aesSecretKey,
            String aesSecretVector
    ) {
        Instant expiresAt = Instant.now().plusSeconds(Math.max(durationMinutes * 60L - 60L, 60L));
        session.set(new TokenSession(token, expiresAt, userId, userGroupId, aesSecretKey, aesSecretVector));
        log.info(">>> [LOGIN] Token enregistré (userId={}), expire à {}", userId, expiresAt);
    }

    /** Compat : met à jour uniquement le token en conservant le reste de la session. */
    public void setToken(String token, int durationMinutes) {
        TokenSession current = session.get();
        Instant expiresAt = Instant.now().plusSeconds(Math.max(durationMinutes * 60L - 60L, 60L));
        if (current == null) {
            session.set(new TokenSession(token, expiresAt, null, null, null, null));
        } else {
            session.set(new TokenSession(
                    token,
                    expiresAt,
                    current.userId(),
                    current.userGroupId(),
                    current.aesSecretKey(),
                    current.aesSecretVector()
            ));
        }
        log.info(">>> [LOGIN] Token renouvelé, expire à {}", expiresAt);
    }

    public Optional<String> getToken() {
        return currentValid().map(TokenSession::token);
    }

    public Optional<String> getUserId() {
        return currentValid().map(TokenSession::userId);
    }

    public Optional<String> getUserGroupId() {
        return currentValid().map(TokenSession::userGroupId);
    }

    public Optional<String> getAesSecretKey() {
        return currentValid().map(TokenSession::aesSecretKey);
    }

    public Optional<String> getAesSecretVector() {
        return currentValid().map(TokenSession::aesSecretVector);
    }

    public Optional<Instant> getExpiresAt() {
        return currentValid().map(TokenSession::expiresAt);
    }

    public boolean hasValidToken() {
        return getToken().isPresent();
    }

    public boolean hasMqCrypto() {
        return currentValid()
                .filter(s -> s.aesSecretKey() != null && !s.aesSecretKey().isBlank())
                .filter(s -> s.aesSecretVector() != null && !s.aesSecretVector().isBlank())
                .isPresent();
    }

    public void clear() {
        session.set(null);
        log.info(">>> [LOGIN] Token DSS supprimé");
    }

    private Optional<TokenSession> currentValid() {
        TokenSession current = session.get();
        if (current == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(current.expiresAt())) {
            clear();
            return Optional.empty();
        }
        return Optional.of(current);
    }

    private record TokenSession(
            String token,
            Instant expiresAt,
            String userId,
            String userGroupId,
            String aesSecretKey,
            String aesSecretVector
    ) {
    }
}
