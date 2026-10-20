package com.company.dss.sync;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.dss.config.DssProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 00:05 Africa/Libreville : aligne les jours clos après le curseur jusqu'à hier.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClosedDaySyncScheduler {

    private final DssProperties dssProperties;
    private final HistorySyncService historySyncService;

    @Scheduled(cron = "${dss.closed-day-sync-cron:0 5 0 * * *}", zone = "Africa/Libreville")
    public void alignClosedDaysFromCursor() {
        if (!dssProperties.isClosedDaySyncEnabled()) {
            return;
        }
        if (historySyncService.isRunning()) {
            log.debug(">>> [SYNC] Clôture journalière ignorée — sync déjà en cours");
            return;
        }
        try {
            log.info(">>> [SYNC] Job minuit — rattrapage curseur jusqu'à hier");
            historySyncService.startCursorCatchUp();
        } catch (Exception ex) {
            log.warn(">>> [SYNC] Clôture journalière échouée : {}", ex.getMessage());
        }
    }
}
