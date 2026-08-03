package com.company.dss.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.config.DssProperties;
import com.company.dss.mq.MqConnectionService;
import com.company.dss.mq.MqEventBuffer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/mq")
@RequiredArgsConstructor
public class MqController {

    private final MqConnectionService mqConnectionService;
    private final MqEventBuffer mqEventBuffer;
    private final DssProperties dssProperties;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", dssProperties.isMqEnabled());
        body.put("connected", mqConnectionService.isConnected());
        body.put("brokerUrl", mqConnectionService.getBrokerUrl() != null ? mqConnectionService.getBrokerUrl() : "");
        body.put("topics", mqConnectionService.getSubscribedTopics());
        body.put("capturedEvents", mqConnectionService.capturedEventCount());
        body.put("lastReceivedAt", mqEventBuffer.lastReceivedAt() != null
                ? mqEventBuffer.lastReceivedAt().toString() : "");
        body.put("lastError", mqConnectionService.getLastError() != null
                ? mqConnectionService.getLastError() : "");
        return ResponseEntity.ok(body);
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        mqConnectionService.start();
        return status();
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        mqConnectionService.stop();
        return status();
    }

    @GetMapping("/events")
    public ResponseEntity<?> events(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean peopleCountingOnly
    ) {
        return ResponseEntity.ok(peopleCountingOnly
                ? mqEventBuffer.recentPeopleCounting(limit)
                : mqEventBuffer.recent(limit));
    }

    /** Relogue en console tous les événements déjà capturés en mémoire. */
    @PostMapping("/events/log")
    public ResponseEntity<Map<String, Object>> logBufferedEvents(
            @RequestParam(defaultValue = "50") int limit
    ) {
        var events = mqEventBuffer.recent(limit);
        log.info("============================================================");
        log.info(">>> [CAPTURE] Dump buffer — {} événement(s)", events.size());
        for (var event : events) {
            log.info("------------------------------------------------------------");
            log.info(">>> [CAPTURE] receivedAt={}, topic={}, method={}, alarmType={}, peopleCounting={}",
                    event.receivedAt(), event.topic(), event.method(), event.alarmType(), event.peopleCounting());
            log.info(">>> [CAPTURE] data = {}", event.payload());
        }
        log.info("============================================================");
        return ResponseEntity.ok(Map.of(
                "logged", events.size(),
                "message", "Événements écrits dans la console applicative"
        ));
    }
}
