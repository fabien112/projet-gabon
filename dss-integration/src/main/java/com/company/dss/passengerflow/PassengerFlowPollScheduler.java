package com.company.dss.passengerflow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.dss.authentication.TokenHolder;
import com.company.dss.config.DssProperties;
import com.company.dss.persistence.PeopleCountingSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Poll périodique People Counting → logs + upsert SQL pour le rapport personnalisé.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassengerFlowPollScheduler {

    private final DssProperties dssProperties;
    private final TokenHolder tokenHolder;
    private final PassengerFlowClient passengerFlowClient;
    private final PeopleCountingSyncService syncService;
    private final ObjectMapper objectMapper;

    private final AtomicReference<List<String>> channelIds = new AtomicReference<>(List.of());
    private final AtomicReference<String> lastSnapshot = new AtomicReference<>("");

    @Scheduled(fixedDelayString = "${dss.passenger-flow-poll-interval:60s}")
    public void pollAndLog() {
        if (!dssProperties.isPassengerFlowPollEnabled()) {
            return;
        }
        if (!tokenHolder.hasValidToken()) {
            return;
        }
        try {
            List<String> ids = channelIds.get();
            if (ids.isEmpty()) {
                ids = passengerFlowClient.discoverCompteuseChannelIds();
                channelIds.set(List.copyOf(ids));
                log.info(">>> [FLOW] Canaux Compteuse découverts : {}", ids);
            }
            if (ids.isEmpty()) {
                log.warn(">>> [FLOW] Aucun canal Compteuse trouvé");
                return;
            }

            var date = passengerFlowClient.todayInGabon();
            List<Map<String, Object>> rows = passengerFlowClient.fetchHistoryForDate(date, ids);
            int saved = syncService.upsertRows(rows);

            List<Map<String, Object>> active = rows.stream()
                    .filter(r -> ((Number) r.getOrDefault("total", 0)).intValue() > 0)
                    .toList();

            String snapshot = objectMapper.writeValueAsString(active);
            if (snapshot.equals(lastSnapshot.get())) {
                log.info(">>> [FLOW] Poll OK — date={}, upsert={}, créneaux actifs={}, inchangé",
                        date, saved, active.size());
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
        } catch (Exception ex) {
            log.warn(">>> [FLOW] Échec poll : {}", ex.getMessage());
        }
    }
}
