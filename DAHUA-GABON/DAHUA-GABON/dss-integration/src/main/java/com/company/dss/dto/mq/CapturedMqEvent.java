package com.company.dss.dto.mq;

import java.time.Instant;
import java.util.Map;

public record CapturedMqEvent(
        Instant receivedAt,
        String topic,
        String method,
        String alarmType,
        boolean peopleCounting,
        Map<String, Object> payload
) {
}
