package com.company.dss.sync;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.company.dss.config.DssProperties;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupSyncRunner {

    private static final ZoneId GABON = ZoneId.of("Africa/Libreville");

    private final DssProperties dssProperties;
    private final HistorySyncService historySyncService;
    private final PeopleCountingHourlyRepository hourlyRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!dssProperties.isStartupFullSyncEnabled()) {
            log.info("Startup full-sync désactivée (dss.startup-full-sync-enabled=false)");
            return;
        }
        LocalDate today = LocalDate.now(GABON);
        LocalDate maxInDb = hourlyRepository.findMaxSlotDate().orElse(null);
        var plan = CatchUpAdvisor.resolveSyncRange(
                today,
                maxInDb,
                null,
                java.time.Instant.now(),
                dssProperties.getStartupFullSyncDays()
        );
        if ("TODAY".equals(plan.kind())) {
            log.info("Startup full-sync skipped: base à jour ({})", today);
            return;
        }
        log.info("Startup full-sync: {} — démarrage {} → {}", plan.message(), plan.from(), plan.to());
        try {
            historySyncService.startMissingDataSync();
        } catch (Exception ex) {
            log.warn("Startup full-sync failed to start: {}", ex.getMessage());
        }
    }
}
