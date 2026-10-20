package com.company.dss.sync;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.company.dss.passengerflow.PassengerFlowPollScheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupSyncRunner {

    private final HistorySyncService historySyncService;
    private final PassengerFlowPollScheduler pollScheduler;

    /** Après {@link com.company.dss.config.DssStartupRunner} (session DSS). */
    @Order(50)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        pollTodayFromDss();
        try {
            historySyncService.startCursorCatchUp();
        } catch (Exception ex) {
            log.warn("Rattrapage curseur au démarrage : {}", ex.getMessage());
        }
    }

    private void pollTodayFromDss() {
        try {
            log.info("Démarrage — récupération automatique des données du jour depuis DSS");
            pollScheduler.pollAndLog();
        } catch (Exception ex) {
            log.warn("Poll du jour au démarrage impossible : {}", ex.getMessage());
        }
    }
}
