package com.company.dss.authentication;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.dss.mq.MqConnectionService;

import lombok.extern.slf4j.Slf4j;

/**
 * Maintient la session DSS active via keep-alive périodique.
 */
@Slf4j
@Component
public class TokenKeepAliveScheduler {

    private final AuthenticationClient authenticationClient;
    private final TokenHolder tokenHolder;
    private final MqConnectionService mqConnectionService;
    private final AtomicLong keepAliveCount = new AtomicLong();

    public TokenKeepAliveScheduler(
            AuthenticationClient authenticationClient,
            TokenHolder tokenHolder,
            @Lazy MqConnectionService mqConnectionService
    ) {
        this.authenticationClient = authenticationClient;
        this.tokenHolder = tokenHolder;
        this.mqConnectionService = mqConnectionService;
    }

    @Scheduled(fixedDelayString = "${dss.keep-alive-interval:25s}")
    public void sendKeepAlive() {
        if (!tokenHolder.hasValidToken()) {
            return;
        }
        long n = keepAliveCount.incrementAndGet();
        try {
            authenticationClient.keepAlive();
            log.info(">>> [KEEP-ALIVE] OK #{} — expire={} | MQ connected={} events={}",
                    n,
                    tokenHolder.getExpiresAt().orElse(null),
                    mqConnectionService.isConnected(),
                    mqConnectionService.capturedEventCount());
        } catch (Exception ex) {
            log.warn(">>> [KEEP-ALIVE] ÉCHEC #{} — token réinitialisé : {}", n, ex.getMessage());
            tokenHolder.clear();
        }
    }
}
