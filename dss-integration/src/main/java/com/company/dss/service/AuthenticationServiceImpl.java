package com.company.dss.service;

import org.springframework.stereotype.Service;

import com.company.dss.authentication.AuthenticationClient;
import com.company.dss.authentication.TokenHolder;
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
    private final MqConnectionService mqConnectionService;

    @Override
    public AuthLoginResponse login() {
        AuthLoginResponse response = authenticationClient.login();
        try {
            log.info("------------------------------------------------------------");
            log.info(">>> [MQ] Démarrage connexion ActiveMQ...");
            mqConnectionService.start();
            log.info(">>> [MQ] Connexion ActiveMQ OK");
        } catch (Exception ex) {
            log.warn(">>> [MQ] Login OK mais démarrage MQ échoué : {}", ex.getMessage());
        }
        return response;
    }

    @Override
    public LoginTestResponse testLogin() {
        AuthLoginResponse response = login();
        return new LoginTestResponse(true, response.token());
    }

    @Override
    public void logout() {
        try {
            mqConnectionService.stop();
        } catch (Exception ex) {
            log.warn("Arrêt MQ lors du logout : {}", ex.getMessage());
        }
        authenticationClient.logout();
    }

    @Override
    public boolean isConnected() {
        return tokenHolder.hasValidToken();
    }

    @Override
    public String getCurrentToken() {
        return tokenHolder.getToken().orElse(null);
    }
}
