package com.company.dss.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.company.dss.service.AuthenticationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Au démarrage : restaure ou établit la session DSS + MQ (après que le contexte Spring est prêt).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DssStartupRunner {

    private final DssProperties dssProperties;
    private final AuthenticationService authenticationService;

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        authenticationService.initializeSessionOnStartup();

        if (!dssProperties.isAutoLogin()) {
            log.info(">>> [START] auto-login désactivé (DSS_AUTO_LOGIN=false)");
            return;
        }
        if (!dssProperties.isConfigured()) {
            log.warn(">>> [START] credentials DSS incomplets — login manuel requis");
            return;
        }

        if (authenticationService.isConnected()) {
            log.info(">>> [READY] Session DSS active — keep-alive actif");
            log.info("============================================================");
        }
    }
}
