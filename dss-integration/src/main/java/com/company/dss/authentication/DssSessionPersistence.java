package com.company.dss.authentication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Sauvegarde la session DSS sur disque pour survivre aux redémarrages
 * et éviter un relogin systématique (source fréquente de « The user has logged in »).
 */
@Slf4j
@Component
public class DssSessionPersistence {

    private final ObjectMapper objectMapper;
    private final Path sessionFile;

    public DssSessionPersistence(ObjectMapper objectMapper, @Value("${spring.datasource.url:}") String dbUrl) {
        this.objectMapper = objectMapper;
        this.sessionFile = resolveSessionPath(dbUrl);
    }

    static Path resolveSessionPath(String dbUrl) {
        if (dbUrl != null && dbUrl.startsWith("jdbc:h2:file:")) {
            String filePart = dbUrl.substring("jdbc:h2:file:".length());
            int semi = filePart.indexOf(';');
            if (semi >= 0) {
                filePart = filePart.substring(0, semi);
            }
            return Path.of(filePart + "-session.json");
        }
        return Path.of("data", "dss-session.json");
    }

    public void save(TokenHolder.TokenSnapshot snapshot) {
        if (snapshot == null || snapshot.token() == null || snapshot.token().isBlank()) {
            return;
        }
        try {
            Files.createDirectories(sessionFile.getParent());
            objectMapper.writeValue(sessionFile.toFile(), snapshot);
            log.debug(">>> [LOGIN] Session DSS persistée ({})", sessionFile);
        } catch (IOException ex) {
            log.warn(">>> [LOGIN] Impossible de persister la session DSS : {}", ex.getMessage());
        }
    }

    public Optional<TokenHolder.TokenSnapshot> load() {
        if (!Files.isRegularFile(sessionFile)) {
            return Optional.empty();
        }
        try {
            TokenHolder.TokenSnapshot snapshot = objectMapper.readValue(sessionFile.toFile(), TokenHolder.TokenSnapshot.class);
            if (snapshot.token() == null || snapshot.token().isBlank()) {
                return Optional.empty();
            }
            if (snapshot.expiresAt() != null && Instant.now().isAfter(snapshot.expiresAt())) {
                log.info(">>> [LOGIN] Session persistée expirée ({})", sessionFile);
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (IOException ex) {
            log.warn(">>> [LOGIN] Lecture session persistée impossible : {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(sessionFile);
        } catch (IOException ex) {
            log.debug(">>> [LOGIN] Suppression session persistée : {}", ex.getMessage());
        }
    }
}
