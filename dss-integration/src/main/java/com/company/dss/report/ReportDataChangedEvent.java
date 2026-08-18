package com.company.dss.report;

import java.time.LocalDate;

/** Émis lorsque des créneaux horaires sont écrits ou mis à jour en base. */
public record ReportDataChangedEvent(LocalDate fromDate, LocalDate toDate, int rowsUpserted) {
}
