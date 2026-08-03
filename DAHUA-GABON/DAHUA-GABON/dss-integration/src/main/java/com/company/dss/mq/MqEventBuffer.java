package com.company.dss.mq;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

import com.company.dss.dto.mq.CapturedMqEvent;

/**
 * Buffer mémoire des derniers événements MQ capturés (en attendant MySQL).
 */
@Component
public class MqEventBuffer {

    private static final int MAX_EVENTS = 200;

    private final ConcurrentLinkedDeque<CapturedMqEvent> events = new ConcurrentLinkedDeque<>();

    public void add(CapturedMqEvent event) {
        events.addFirst(event);
        while (events.size() > MAX_EVENTS) {
            events.removeLast();
        }
    }

    public List<CapturedMqEvent> recent(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_EVENTS));
        List<CapturedMqEvent> result = new ArrayList<>(size);
        int i = 0;
        for (CapturedMqEvent event : events) {
            if (i++ >= size) {
                break;
            }
            result.add(event);
        }
        return Collections.unmodifiableList(result);
    }

    public List<CapturedMqEvent> recentPeopleCounting(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_EVENTS));
        List<CapturedMqEvent> result = new ArrayList<>(size);
        for (CapturedMqEvent event : events) {
            if (event.peopleCounting()) {
                result.add(event);
                if (result.size() >= size) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public int size() {
        return events.size();
    }

    public Instant lastReceivedAt() {
        CapturedMqEvent first = events.peekFirst();
        return first != null ? first.receivedAt() : null;
    }

    public void clear() {
        events.clear();
    }
}
