package com.company.dss.passengerflow;

import java.time.Instant;
import java.time.LocalDate;

/**
 * État du poll automatique People Counting (DSS → BD locale).
 */
public record PassengerFlowPollStatus(
        boolean enabled,
        boolean inProgress,
        boolean suspended,
        String interval,
        Instant lastAt,
        String lastStatus,
        String lastMessage,
        int lastRowsUpserted,
        int lastActiveSlots,
        LocalDate lastDate
) {
    public static PassengerFlowPollStatus idle(boolean enabled, String interval) {
        return new PassengerFlowPollStatus(
                enabled, false, false, interval, null, "IDLE",
                "Aucun poll encore effectué", 0, 0, null
        );
    }
}
