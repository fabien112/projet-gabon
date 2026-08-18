package com.company.dss.report;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReportEventService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        return emitter;
    }

    @EventListener
    public void onReportDataChanged(ReportDataChangedEvent event) {
        if (emitters.isEmpty() || event.fromDate() == null || event.toDate() == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromDate", event.fromDate());
        payload.put("toDate", event.toDate());
        payload.put("rowsUpserted", event.rowsUpserted());
        broadcast(payload);
    }

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
                log.debug("Report SSE keepalive failed: {}", ex.getMessage());
            }
        }
    }

    private void broadcast(Map<String, Object> payload) {
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().name("data-changed").data(payload));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(emitter);
            }
        }
    }
}
