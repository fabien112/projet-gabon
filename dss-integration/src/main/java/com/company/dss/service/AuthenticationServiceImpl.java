package com.company.dss.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.company.dss.authentication.AuthenticationClient;
import com.company.dss.authentication.DssSessionPersistence;
import com.company.dss.authentication.TokenHolder;
import com.company.dss.config.DssProperties;
import com.company.dss.dto.authentication.AuthLoginResponse;
import com.company.dss.dto.authentication.LoginTestResponse;
import com.company.dss.mq.MqConnectionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationClient authenticationClient;
    private final TokenHolder tokenHolder;
    private final DssSessionPersistence sessionPersistence;
    private final MqConnectionService mqConnectionService;
    private final DssProperties dssProperties;

    private final Object loginLock = new Object();
    private final AtomicBoolean sessionReady = new AtomicBoolean(false);

    @Override
    public AuthLoginResponse login() {
        synchronized (loginLock) {
            AuthLoginResponse response = authenticationClient.loginWithRecovery(true);
            startMqQuietly();
            return response;
        }
    }

    @Override
    public void initializeSessionOnStartup() {
        synchronized (loginLock) {
            try {
                if (!dssProperties.isAutoLogin() || !dssProperties.isConfigured()) {
                    return;
                }
                log.info("============================================================");
                log.info(">>> [START] Connexion DSS ({}@{}:{})",
                        dssProperties.getUsername(), dssProperties.getHost(), dssProperties.getPort());
                log.info("============================================================");
                sessionPersistence.load().ifPresent(tokenHolder::restore);
                if (tokenHolder.hasValidToken() && authenticationClient.validatePersistedSession()) {
                    log.info(">>> [LOGIN] Session DSS restaurée depuis le disque — keep-alive OK");
                    startMqQuietly();
                    return;
                }
                tokenHolder.clear();
                authenticationClient.loginWithRecovery(true);
                startMqQuietly();
            } catch (Exception ex) {
                log.error(">>> [START] Échec login/MQ au démarrage : {}", ex.getMessage());
                log.error(">>> [START] Nouvelle tentative automatique via keep-alive…");
            } finally {
                sessionReady.set(true);
            }
        }
    }

    @Override
    public boolean isSessionReady() {
        return sessionReady.get();
    }

    @Override
    public AuthLoginResponse ensureLoggedIn() {
        if (tokenHolder.hasValidToken()) {
            return null;
        }
        synchronized (loginLock) {
            if (tokenHolder.hasValidToken()) {
                return null;
            }
            if (!dssProperties.isAutoLogin()) {
                throw new IllegalStateException(
                        "Session DSS inactive et auto-login désactivé. Relancez le login DSS."
                );
            }
            log.info(">>> [LOGIN] Session DSS inactive — relogin automatique…");
            AuthLoginResponse response = authenticationClient.loginWithRecovery(true);
            startMqQuietly();
            return response;
        }
    }

    @Override
    public AuthLoginResponse forceReconnect() {
        synchronized (loginLock) {
            authenticationClient.logout();
            AuthLoginResponse response = authenticationClient.loginWithRecovery(true);
            startMqQuietly();
            return response;
        }
    }

    @Override
    public LoginTestResponse testLogin() {
        AuthLoginResponse response = login();
        return new LoginTestResponse(true, response.token());
    }

    @Override
    public void logout() {
        synchronized (loginLock) {
            try {
                mqConnectionService.stop();
            } catch (Exception ex) {
                log.warn("Arrêt MQ lors du logout : {}", ex.getMessage());
            }
            authenticationClient.logout();
        }
    }

    @Override
    public boolean isConnected() {
        return tokenHolder.hasValidToken();
    }

    @Override
    public String getCurrentToken() {
        return tokenHolder.getToken().orElse(null);
    }

    private void startMqQuietly() {
        if (!dssProperties.isMqEnabled()) {
            return;
        }
        try {
            log.info(">>> [MQ] Démarrage connexion ActiveMQ...");
            mqConnectionService.start();
            log.info(">>> [MQ] Connexion ActiveMQ OK");
        } catch (Exception ex) {
            log.warn(">>> [MQ] Login OK mais démarrage MQ échoué : {}", ex.getMessage());
        }
    }
}
