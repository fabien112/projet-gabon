package com.company.dss.mq;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.company.dss.dto.mq.CapturedMqEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Parse et filtre les messages MQ DSS, en capturant les People Counting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqMessageHandler {

    /** Types d'alarme People Counting (doc DSS 6.1.7). */
    private static final Set<String> PEOPLE_COUNTING_ALARM_TYPES = Set.of("316", "826", "17");

    private final ObjectMapper objectMapper;
    private final MqEventBuffer eventBuffer;

    public void handle(String topic, String rawText) {
        try {
            Map<String, Object> payload = objectMapper.readValue(rawText, new TypeReference<>() {
            });
            String method = stringValue(payload.get("method"));
            String alarmType = extractAlarmType(payload);
            boolean peopleCounting = isPeopleCounting(alarmType, method, payload);

            CapturedMqEvent event = new CapturedMqEvent(
                    Instant.now(),
                    topic,
                    method,
                    alarmType,
                    peopleCounting,
                    payload
            );
            eventBuffer.add(event);

            String payloadJson = objectMapper.writeValueAsString(payload);
            if (peopleCounting) {
                Map<String, Object> summary = buildCaptureSummary(payload, topic, method, alarmType);
                log.info("============================================================");
                log.info(">>> [CAPTURE] People Counting reçu");
                summary.forEach((k, v) -> log.info(">>> [CAPTURE]   {} = {}", k, v));
                log.info(">>> [CAPTURE] data = {}", payloadJson);
                log.info("============================================================");
            } else {
                log.info("============================================================");
                log.info(">>> [CAPTURE] Événement MQ reçu");
                log.info(">>> [CAPTURE]   topic = {}", topic);
                log.info(">>> [CAPTURE]   method = {}", method);
                log.info(">>> [CAPTURE]   alarmType = {}", alarmType);
                log.info(">>> [CAPTURE]   receivedAt = {}", event.receivedAt());
                log.info(">>> [CAPTURE] data = {}", payloadJson);
                log.info("============================================================");
            }
        } catch (Exception ex) {
            log.warn(">>> [CAPTURE] Message non JSON ou invalide sur {} : {}", topic, ex.getMessage());
            log.info(">>> [CAPTURE] raw = {}", truncate(rawText, 2000));
            eventBuffer.add(new CapturedMqEvent(
                    Instant.now(),
                    topic,
                    null,
                    null,
                    false,
                    Map.of("raw", rawText)
            ));
        }
    }

    private Map<String, Object> buildCaptureSummary(
            Map<String, Object> payload,
            String topic,
            String method,
            String alarmType
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("topic", topic);
        summary.put("method", method);
        summary.put("alarmType", alarmType);
        summary.put("receivedAt", Instant.now().toString());

        Object info = payload.get("info");
        if (info instanceof Map<?, ?> infoMap) {
            putIfPresent(summary, "deviceCode", infoMap.get("deviceCode"));
            putIfPresent(summary, "deviceName", infoMap.get("deviceName"));
            putIfPresent(summary, "channelId", infoMap.get("channelId"));
            putIfPresent(summary, "channelName", infoMap.get("channelName"));
            putIfPresent(summary, "channelSeq", infoMap.get("channelSeq"));
            putIfPresent(summary, "alarmDate", infoMap.get("alarmDate"));
            putIfPresent(summary, "alarmCode", infoMap.get("alarmCode"));
            putIfPresent(summary, "nodeCode", infoMap.get("nodeCode"));

            Object extend = infoMap.get("extend");
            if (extend instanceof Map<?, ?> extendMap) {
                putIfPresent(summary, "enterNumber", firstPresent(extendMap, "enterNumber", "EnterNumber", "inNum"));
                putIfPresent(summary, "exitNumber", firstPresent(extendMap, "exitNumber", "ExitNumber", "outNum"));
                putIfPresent(summary, "passNumber", firstPresent(extendMap, "passNumber", "PassNumber"));
                putIfPresent(summary, "peopleNum", firstPresent(extendMap, "peopleNum", "PeopleNum", "number"));
            }
            Object data = infoMap.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                putIfPresent(summary, "enterNumber", firstPresent(dataMap, "enterNumber", "EnterNumber", "inNum"));
                putIfPresent(summary, "exitNumber", firstPresent(dataMap, "ExitNumber", "exitNumber", "outNum"));
            }
        }

        putIfPresent(summary, "category", payload.get("category"));
        putIfPresent(summary, "id", payload.get("id"));
        return summary;
    }

    private boolean isPeopleCounting(String alarmType, String method, Map<String, Object> payload) {
        if (alarmType != null && PEOPLE_COUNTING_ALARM_TYPES.contains(alarmType)) {
            return true;
        }
        if (method != null) {
            String lower = method.toLowerCase();
            if (lower.contains("people") || lower.contains("passenger") || lower.contains("flow")) {
                return true;
            }
        }
        Object info = payload.get("info");
        if (info instanceof Map<?, ?> infoMap) {
            Object type = infoMap.get("alarmType");
            if (type != null && PEOPLE_COUNTING_ALARM_TYPES.contains(String.valueOf(type))) {
                return true;
            }
        }
        return false;
    }

    private String extractAlarmType(Map<String, Object> payload) {
        Object direct = payload.get("alarmType");
        if (direct != null) {
            return String.valueOf(direct);
        }
        Object info = payload.get("info");
        if (info instanceof Map<?, ?> infoMap && infoMap.get("alarmType") != null) {
            return String.valueOf(infoMap.get("alarmType"));
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank() && !target.containsKey(key)) {
            target.put(key, value);
        }
    }

    private Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
