package com.company.dss.sync;

import java.time.Instant;
import java.time.LocalDate;

public record HistorySyncStatus(
        boolean running,
        String phase,
        LocalDate fromDate,
        LocalDate toDate,
        LocalDate currentDate,
        int daysTotal,
        int daysDone,
        int rowsUpserted,
        String message,
        Instant startedAt,
        Instant finishedAt,
        LocalDate lastSuccessfulSyncAt,
        LocalDate lastCoveredDateInDb,
        Instant lastJobFinishedAt,
        String lastJobStatus,
        String lastJobMessage,
        LocalDate lastJobFrom,
        LocalDate lastJobTo,
        boolean dssSessionActive,
        // Poll automatique (DSS → BD)
        boolean pollEnabled,
        boolean pollInProgress,
        boolean pollSuspended,
        String pollInterval,
        Instant pollLastAt,
        String pollLastStatus,
        String pollLastMessage,
        int pollLastRowsUpserted,
        int pollLastActiveSlots,
        LocalDate pollLastDate,
        boolean catchUpNeeded,
        String catchUpKind,
        LocalDate catchUpFrom,
        LocalDate catchUpTo,
        String catchUpMessage
) {
}
