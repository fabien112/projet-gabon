package com.company.dss.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.company.dss.mq.MqConnectionService;
import com.company.dss.service.AuthenticationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Au démarrage : login DSS + connexion MQ, avec étapes visibles en console.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DssStartupRunner implements ApplicationRunner {

    private final DssProperties dssProperties;
    private final AuthenticationService authenticationService;
    private final MqConnectionService mqConnectionService;

    @Override
    public void run(ApplicationArguments args) {
        if (!dssProperties.isAutoLogin()) {
            log.info(">>> [START] auto-login désactivé (DSS_AUTO_LOGIN=false) — appeler POST /api/auth/login");
            return;
        }
        if (!dssProperties.isConfigured()) {
            log.warn(">>> [START] credentials DSS incomplets — login manuel requis");
            return;
        }

        log.info("============================================================");
        log.info(">>> [START] Démarrage intégration DSS ({}@{}:{})",
                dssProperties.getUsername(), dssProperties.getHost(), dssProperties.getPort());
        log.info("============================================================");

        try {
            log.info(">>> [LOGIN] Connexion en cours...");
            authenticationService.login();
            log.info(">>> [LOGIN] OK — session DSS active");
            if (dssProperties.isMqEnabled() && mqConnectionService.isConnected()) {
                log.info(">>> [READY] Login + MQ OK — keep-alive actif, en attente d'événements");
            } else if (dssProperties.isMqEnabled()) {
                log.warn(">>> [READY] Login OK mais MQ non connecté — voir logs [MQ]");
            } else {
                log.info(">>> [READY] Login OK — MQ désactivé");
            }
            log.info("============================================================");
        } catch (Exception ex) {
            log.error(">>> [START] Échec login/MQ au démarrage : {}", ex.getMessage());
            log.error(">>> [START] Relancer via POST /api/auth/login après correction");
        }
    }
}
