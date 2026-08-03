package com.company.dss.authentication;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintient la session DSS active via keep-alive périodique.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenKeepAliveScheduler {

    private final AuthenticationClient authenticationClient;
    private final TokenHolder tokenHolder;

    @Scheduled(fixedDelayString = "${dss.keep-alive-interval:25s}")
    public void sendKeepAlive() {
        if (!tokenHolder.hasValidToken()) {
            return;
        }
        try {
            authenticationClient.keepAlive();
        } catch (Exception ex) {
            log.warn("Keep-alive DSS échoué, le token sera réinitialisé : {}", ex.getMessage());
            tokenHolder.clear();
        }
    }
}
