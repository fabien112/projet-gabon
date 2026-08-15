package com.company.dss.passengerflow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.dss.authentication.TokenHolder;
import com.company.dss.camera.CameraService;
import com.company.dss.config.DssProperties;
import com.company.dss.persistence.PeopleCountingSyncService;
import com.company.dss.sync.HistorySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Poll périodique People Counting → logs + upsert SQL pour le rapport personnalisé.
 * Suspendu pendant une sync historique pour éviter le rate-limit DSS (HTTP 429).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassengerFlowPollScheduler {

    private final DssProperties dssProperties;
    private final TokenHolder tokenHolder;
    private final PassengerFlowClient passengerFlowClient;
    private final PeopleCountingSyncService syncService;
    private final HistorySyncService historySyncService;
    private final CameraService cameraService;

    private final AtomicReference<String> lastFingerprint = new AtomicReference<>("");
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final AtomicReference<PassengerFlowPollStatus> lastStatus =
            new AtomicReference<>(PassengerFlowPollStatus.idle(true, "60s"));

    public PassengerFlowPollStatus status() {
        boolean enabled = dssProperties.isPassengerFlowPollEnabled();
        boolean suspended = historySyncService.isRunning();
        PassengerFlowPollStatus last = lastStatus.get();
        return new PassengerFlowPollStatus(
                enabled,
                inProgress.get(),
                suspended,
                formatInterval(dssProperties.getPassengerFlowPollInterval()),
                last.lastAt(),
                last.lastStatus(),
                last.lastMessage(),
                last.lastRowsUpserted(),
                last.lastActiveSlots(),
                last.lastDate()
        );
    }

    @Scheduled(fixedDelayString = "${dss.passenger-flow-poll-interval:60s}")
    public void pollAndLog() {
        if (!dssProperties.isPassengerFlowPollEnabled()) {
            return;
        }
        if (historySyncService.isRunning()) {
            log.debug(">>> [FLOW] Poll suspendu — sync historique en cours");
            remember("SKIPPED", "Suspendu — sync historique en cours", 0, 0, null);
            return;
        }
        if (!tokenHolder.hasValidToken()) {
            remember("SKIPPED", "Session DSS inactive", 0, 0, null);
            return;
        }
        if (!inProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            List<String> ids = cameraService.channelIdsForSync();
            if (ids.isEmpty()) {
                log.warn(">>> [FLOW] Aucune caméra configurée");
                remember("FAILED", "Aucune caméra configurée — ajoutez-en une dans Config", 0, 0, null);
                return;
            }

            LocalDate date = passengerFlowClient.todayInGabon();
            List<Map<String, Object>> rows = passengerFlowClient.fetchHistoryForDate(date, ids);
            int activeSlots = (int) rows.stream()
                    .filter(r -> ((Number) r.getOrDefault("total", 0)).intValue() > 0)
                    .count();

            String fingerprint = fingerprint(rows);
            if (fingerprint.equals(lastFingerprint.get())) {
                log.debug(">>> [FLOW] Poll OK — date={}, créneaux={}, inchangé (pas d'écriture SQL)",
                        date, rows.size());
                remember("OK", "Poll OK — données inchangées", 0, activeSlots, date);
                return;
            }

            int saved = syncService.upsertRows(rows);
            lastFingerprint.set(fingerprint);
            log.info(">>> [FLOW] Poll OK — date={} — DSS={} ligne(s) — écrites={} — actifs={}",
                    date, rows.size(), saved, activeSlots);
            remember("OK", "Poll OK — " + saved + " ligne(s) écrite(s), " + activeSlots + " créneau(x) actif(s)",
                    saved, activeSlots, date);
        } catch (Exception ex) {
            log.warn(">>> [FLOW] Échec poll : {}", ex.getMessage());
            remember("FAILED", "Échec : " + ex.getMessage(), 0, 0, null);
        } finally {
            inProgress.set(false);
        }
    }

    static String fingerprint(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(r -> r.getOrDefault("channelId", "") + "|"
                        + r.getOrDefault("startTime", "") + "|"
                        + r.getOrDefault("in", 0) + "|"
                        + r.getOrDefault("out", 0) + "|"
                        + r.getOrDefault("occupancy", 0))
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    private void remember(
            String status,
            String message,
            int rows,
            int activeSlots,
            LocalDate date
    ) {
        lastStatus.set(new PassengerFlowPollStatus(
                dssProperties.isPassengerFlowPollEnabled(),
                false,
                historySyncService.isRunning(),
                formatInterval(dssProperties.getPassengerFlowPollInterval()),
                Instant.now(),
                status,
                message,
                rows,
                activeSlots,
                date
        ));
        if ("OK".equals(status) && date != null) {
            historySyncService.rememberPollSuccess(Instant.now(), date);
        }
    }

    private static String formatInterval(Duration interval) {
        if (interval == null) {
            return "60s";
        }
        long seconds = interval.toSeconds();
        if (seconds > 0 && seconds % 60 == 0) {
            return (seconds / 60) + " min";
        }
        if (seconds > 0) {
            return seconds + "s";
        }
        return interval.toMillis() + "ms";
    }
}
