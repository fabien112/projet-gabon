package com.company.dss.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DssDbReconcileServiceTest {

    @Test
    void alignedWhenEveryDssSlotMatchesDb() {
        LocalDate day = LocalDate.of(2026, 8, 15);
        var key = new DssDbReconcileService.SlotKey("1000004$1$0$0", day, LocalTime.of(9, 0));
        var counts = new DssDbReconcileService.Counts(12, 4);
        Map<DssDbReconcileService.SlotKey, DssDbReconcileService.Counts> dss = new LinkedHashMap<>();
        Map<DssDbReconcileService.SlotKey, DssDbReconcileService.Counts> db = new LinkedHashMap<>();
        dss.put(key, counts);
        db.put(key, counts);

        var report = DssDbReconcileService.buildReport(day, day, 1, dss, db);

        assertThat(report.aligned()).isTrue();
        assertThat(report.matches()).isEqualTo(1);
        assertThat(report.missingInDb()).isZero();
        assertThat(report.valueMismatches()).isZero();
        assertThat(report.dssEntries()).isEqualTo(12);
        assertThat(report.dbEntries()).isEqualTo(12);
    }

    @Test
    void detectsMissingAndValueMismatch() {
        LocalDate day = LocalDate.of(2026, 8, 15);
        var missing = new DssDbReconcileService.SlotKey("1000004$1$0$0", day, LocalTime.of(9, 0));
        var mismatch = new DssDbReconcileService.SlotKey("1000005$1$0$0", day, LocalTime.of(10, 0));
        Map<DssDbReconcileService.SlotKey, DssDbReconcileService.Counts> dss = new LinkedHashMap<>();
        Map<DssDbReconcileService.SlotKey, DssDbReconcileService.Counts> db = new LinkedHashMap<>();
        dss.put(missing, new DssDbReconcileService.Counts(5, 1));
        dss.put(mismatch, new DssDbReconcileService.Counts(8, 2));
        db.put(mismatch, new DssDbReconcileService.Counts(7, 2));

        var report = DssDbReconcileService.buildReport(day, day, 2, dss, db);

        assertThat(report.aligned()).isFalse();
        assertThat(report.missingInDb()).isEqualTo(1);
        assertThat(report.valueMismatches()).isEqualTo(1);
        assertThat(report.matches()).isZero();
        assertThat(report.diffs()).extracting(DssDbReconcileService.DiffSample::type)
                .containsExactly("MISSING_IN_DB", "VALUE");
    }
}
