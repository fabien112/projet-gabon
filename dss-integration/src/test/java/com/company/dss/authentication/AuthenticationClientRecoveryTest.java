package com.company.dss.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.company.dss.exception.DssAuthenticationException;

class AuthenticationClientRecoveryTest {

    @Test
    void isAlreadyLoggedIn_detectsEnglishMessage() {
        assertThat(AuthenticationClient.isAlreadyLoggedIn(
                new DssAuthenticationException("Échec du login DSS : The user has logged in.")))
                .isTrue();
    }

    @Test
    void isAlreadyLoggedIn_detectsErrorCode() {
        assertThat(AuthenticationClient.isAlreadyLoggedIn(
                new DssAuthenticationException("Échec du login DSS (code 4) : Account busy")))
                .isTrue();
    }

    @Test
    void resolveSessionPath_usesH2FileBaseName() {
        Path path = DssSessionPersistence.resolveSessionPath(
                "jdbc:h2:file:C:/data/dss;MODE=MySQL;DB_CLOSE_DELAY=-1");
        assertThat(path.toString().replace('\\', '/')).endsWith("C:/data/dss-session.json");
    }
}
