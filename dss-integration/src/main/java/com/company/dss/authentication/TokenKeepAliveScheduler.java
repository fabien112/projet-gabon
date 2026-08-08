package com.company.dss.authentication;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.dss.config.DssProperties;
import com.company.dss.mq.MqConnectionService;
import com.company.dss.service.AuthenticationService;

import lombok.extern.slf4j.Slf4j;

/**
 * Maintient la session DSS active via keep-alive périodique.
 * En cas d'échec (ex. token expiré côté DSS), relogin automatique si activé.
 */
@Slf4j
@Component
public class TokenKeepAliveScheduler {

    private final AuthenticationClient authenticationClient;
    private final TokenHolder tokenHolder;
    private final MqConnectionService mqConnectionService;
    private final AuthenticationService authenticationService;
    private final DssProperties dssProperties;
    private final AtomicLong keepAliveCount = new AtomicLong();

    public TokenKeepAliveScheduler(
            AuthenticationClient authenticationClient,
            TokenHolder tokenHolder,
            @Lazy MqConnectionService mqConnectionService,
            @Lazy AuthenticationService authenticationService,
            DssProperties dssProperties
    ) {
        this.authenticationClient = authenticationClient;
        this.tokenHolder = tokenHolder;
        this.mqConnectionService = mqConnectionService;
        this.authenticationService = authenticationService;
        this.dssProperties = dssProperties;
    }

    @Scheduled(fixedDelayString = "${dss.keep-alive-interval:25s}")
    public void sendKeepAlive() {
        if (!tokenHolder.hasValidToken()) {
            // Pas de session : tenter un relogin pour ne pas rester bloqué
            if (dssProperties.isAutoLogin() && dssProperties.isConfigured()) {
                try {
                    authenticationService.ensureLoggedIn();
                    log.info(">>> [KEEP-ALIVE] Relogin OK après session vide");
                } catch (Exception ex) {
                    log.warn(">>> [KEEP-ALIVE] Relogin impossible : {}", ex.getMessage());
                }
            }
            return;
        }
        long n = keepAliveCount.incrementAndGet();
        try {
            authenticationClient.keepAlive();
            // Keep-alive DSS prolonge la session côté serveur : on aligne l'expiration locale.
            tokenHolder.extendExpiry(30);
            log.info(">>> [KEEP-ALIVE] OK #{} — expire={} | MQ connected={} events={}",
                    n,
                    tokenHolder.getExpiresAt().orElse(null),
                    mqConnectionService.isConnected(),
                    mqConnectionService.capturedEventCount());
        } catch (Exception ex) {
            log.warn(">>> [KEEP-ALIVE] ÉCHEC #{} — {}", n, ex.getMessage());
            tokenHolder.clear();
            if (dssProperties.isAutoLogin()) {
                try {
                    authenticationService.ensureLoggedIn();
                    log.info(">>> [KEEP-ALIVE] Relogin OK après échec #{}", n);
                } catch (Exception reloginEx) {
                    log.warn(">>> [KEEP-ALIVE] Relogin échoué après #{} : {}", n, reloginEx.getMessage());
                }
            }
        }
    }
}
