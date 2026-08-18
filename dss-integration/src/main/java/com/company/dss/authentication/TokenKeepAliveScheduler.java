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
 * Maintient la session DSS active via keep-alive + renouvellement périodique du token.
 * Ne lâche jamais : relogin automatique en cas d'échec.
 */
@Slf4j
@Component
public class TokenKeepAliveScheduler {

    private static final long UPDATE_TOKEN_EVERY_N = 48;

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

    @Scheduled(initialDelayString = "30s", fixedDelayString = "${dss.keep-alive-interval:25s}")
    public void sendKeepAlive() {
        if (!authenticationService.isSessionReady()) {
            return;
        }
        if (!dssProperties.isAutoLogin() || !dssProperties.isConfigured()) {
            return;
        }

        if (!tokenHolder.hasValidToken()) {
            reloginQuietly("session vide");
            return;
        }

        long n = keepAliveCount.incrementAndGet();
        try {
            if (n % UPDATE_TOKEN_EVERY_N == 0) {
                authenticationClient.updateToken();
                log.info(">>> [KEEP-ALIVE] Token DSS renouvelé (#{})", n);
            } else {
                authenticationClient.keepAlive();
                tokenHolder.extendExpiry(30);
            }
            log.info(">>> [KEEP-ALIVE] OK #{} — expire={} | MQ connected={} events={}",
                    n,
                    tokenHolder.getExpiresAt().orElse(null),
                    mqConnectionService.isConnected(),
                    mqConnectionService.capturedEventCount());
        } catch (Exception ex) {
            log.warn(">>> [KEEP-ALIVE] ÉCHEC #{} — {}", n, ex.getMessage());
            try {
                authenticationClient.updateToken();
                tokenHolder.extendExpiry(30);
                log.info(">>> [KEEP-ALIVE] Récupéré via updateToken après échec #{}", n);
            } catch (Exception updateEx) {
                tokenHolder.clear();
                reloginQuietly("échec keep-alive #" + n);
            }
        }
    }

    private void reloginQuietly(String reason) {
        try {
            authenticationService.ensureLoggedIn();
            log.info(">>> [KEEP-ALIVE] Relogin OK ({})", reason);
        } catch (Exception ex) {
            log.warn(">>> [KEEP-ALIVE] Relogin impossible ({}) : {}", reason, ex.getMessage());
        }
    }
}
