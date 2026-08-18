package com.company.dss.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.dss.config.DssProperties;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlotFinalizerScheduler {

    private final PeopleCountingHourlyRepository hourlyRepository;
    private final DssProperties dssProperties;

    @Scheduled(fixedDelayString = "${dss.slot-finalizer-interval:60s}")
    public void finalizeSlots() {
        int grace = Math.max(0, dssProperties.getSlotFinalizerGraceMinutes());
        // cutoff = now - grace minutes
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(grace);
        LocalDate date = cutoff.toLocalDate();
        LocalTime time = cutoff.toLocalTime();
        try {
            List<com.company.dss.persistence.entity.PeopleCountingHourlyEntity> rows =
                    hourlyRepository.findUnfinalizedBefore(date, time);
            if (rows == null || rows.isEmpty()) {
                return;
            }
            for (com.company.dss.persistence.entity.PeopleCountingHourlyEntity r : rows) {
                r.setFinalized(true);
            }
            hourlyRepository.saveAll(rows);
            log.info("Marked {} slots as finalized (cutoff={})", rows.size(), cutoff);
        } catch (Exception ex) {
            log.warn("Failed to finalize slots: {}", ex.getMessage());
        }
    }
}
