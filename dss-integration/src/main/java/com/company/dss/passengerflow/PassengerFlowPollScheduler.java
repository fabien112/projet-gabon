package com.company.dss.passengerflow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.dss.authentication.TokenHolder;
import com.company.dss.config.DssProperties;
import com.company.dss.persistence.PeopleCountingSyncService;
import com.company.dss.sync.HistorySyncService;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

    private final AtomicReference<List<String>> channelIds = new AtomicReference<>(List.of());
    private final AtomicReference<String> lastSnapshot = new AtomicReference<>("");
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final AtomicReference<PassengerFlowPollStatus> lastStatus =
            new AtomicReference<>(PassengerFlowPollStatus.idle(true, "45s"));

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

    @Scheduled(fixedDelayString = "${dss.passenger-flow-poll-interval:45s}")
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
            List<CompteuseChannel> channels = passengerFlowClient.discoverCompteuseChannels().stream()
                    .filter(c -> CompteuseCameraRules.isPrimary(c.channelId(), c.name(), true))
                    .limit(3)
                    .toList();
            syncService.upsertCameras(channels);
            List<String> ids = channels.stream().map(CompteuseChannel::channelId).toList();
            channelIds.set(List.copyOf(ids));
            if (ids.isEmpty()) {
                log.warn(">>> [FLOW] Aucun canal Compteuse trouvé");
                remember("FAILED", "Aucun canal Compteuse trouvé", 0, 0, null);
                return;
            }

            LocalDate date = passengerFlowClient.todayInGabon();
            List<Map<String, Object>> rows = passengerFlowClient.fetchHistoryForDate(date, ids);
            int saved = syncService.upsertRows(rows);

            List<Map<String, Object>> active = rows.stream()
                    .filter(r -> ((Number) r.getOrDefault("total", 0)).intValue() > 0)
                    .toList();

            String snapshot = objectMapper.writeValueAsString(active);
            if (snapshot.equals(lastSnapshot.get())) {
                log.info(">>> [FLOW] Poll OK — date={}, upsert={}, créneaux actifs={}, inchangé",
                        date, saved, active.size());
                remember("OK", "Poll OK — données inchangées (" + saved + " ligne(s))",
                        saved, active.size(), date);
                return;
            }
            lastSnapshot.set(snapshot);

            log.info("============================================================");
            log.info(">>> [FLOW] Données People Counting — date={} — upsert={} — {} créneau(x) actif(s)",
                    date, saved, active.size());
            for (Map<String, Object> row : active) {
                log.info(">>> [FLOW] data = {}", objectMapper.writeValueAsString(row));
            }
            log.info("============================================================");
            remember("OK", "Poll OK — " + saved + " ligne(s), " + active.size() + " créneau(x) actif(s)",
                    saved, active.size(), date);
        } catch (Exception ex) {
            log.warn(">>> [FLOW] Échec poll : {}", ex.getMessage());
            remember("FAILED", "Échec : " + ex.getMessage(), 0, 0, null);
        } finally {
            inProgress.set(false);
        }
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
    }

    private static String formatInterval(Duration interval) {
        if (interval == null) {
            return "45s";
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
