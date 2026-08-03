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
        LocalDate lastJobTo
) {
}
