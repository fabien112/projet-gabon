package com.company.dss.sync;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.company.dss.passengerflow.PassengerFlowPollScheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncStatusEventService {

    private final HistorySyncService historySyncService;
    private final PassengerFlowPollScheduler passengerFlowPollScheduler;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("status").data(currentStatus()));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    @EventListener
    public void onSyncStatusChanged(SyncStatusChangedEvent event) {
        broadcast();
    }

    /** Commentaire SSE périodique pour garder la connexion ouverte derrière un proxy. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException ex) {
                emitters.remove(emitter);
            } catch (Exception ex) {
                emitters.remove(emitter);
                log.debug("SSE keepalive failed: {}", ex.getMessage());
            }
        }
    }

    private void broadcast() {
        if (emitters.isEmpty()) {
            return;
        }
        HistorySyncStatus status = currentStatus();
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().name("status").data(status));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(emitter);
            }
        }
    }

    private HistorySyncStatus currentStatus() {
        return historySyncService.status(passengerFlowPollScheduler.status());
    }
}
