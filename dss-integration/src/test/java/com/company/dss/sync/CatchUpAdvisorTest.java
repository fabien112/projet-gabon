package com.company.dss.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CatchUpAdvisorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private static final LocalDate SCAN_FROM = TODAY.minusDays(29);

    @Test
    void emptyDatabaseSyncsInitialWindow() {
        var plan = CatchUpAdvisor.resolveSyncRange(TODAY, SCAN_FROM, Set.of(), null, NOW, 30);
        assertThat(plan.kind()).isEqualTo("EMPTY");
        assertThat(plan.from()).isEqualTo(TODAY.minusDays(29));
        assertThat(plan.to()).isEqualTo(TODAY);
    }

    @Test
    void gapAfterDowntimeCoversMissingDaysThroughToday() {
        LocalDate scanFrom = LocalDate.of(2026, 8, 12);
        Set<LocalDate> present = Set.of(scanFrom);
        var plan = CatchUpAdvisor.resolveSyncRange(
                TODAY, scanFrom, present, NOW.minusSeconds(3600), NOW, 30);
        assertThat(plan.kind()).isEqualTo("GAP");
        assertThat(plan.from()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(plan.to()).isEqualTo(TODAY);
    }

    @Test
    void holeInMiddleWhenTodayAndPastSamplePresent() {
        LocalDate scanFrom = LocalDate.of(2026, 8, 1);
        Set<LocalDate> present = Set.of(scanFrom, TODAY);
        var plan = CatchUpAdvisor.resolveSyncRange(
                TODAY, scanFrom, present, NOW.minusSeconds(60), NOW, 30);
        assertThat(plan.kind()).isEqualTo("GAP");
        assertThat(plan.from()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(plan.to()).isEqualTo(TODAY);
    }

    @Test
    void staleTodayResyncsToday() {
        Set<LocalDate> present = allDaysInScanWindow();
        var plan = CatchUpAdvisor.resolveSyncRange(
                TODAY, SCAN_FROM, present, NOW.minusSeconds(20 * 60), NOW, 30);
        assertThat(plan.kind()).isEqualTo("STALE_TODAY");
        assertThat(plan.from()).isEqualTo(TODAY);
        assertThat(plan.to()).isEqualTo(TODAY);
    }

    @Test
    void okWhenTodayCoveredSyncsTodayForMissingSlots() {
        Set<LocalDate> present = allDaysInScanWindow();
        var plan = CatchUpAdvisor.resolveSyncRange(
                TODAY, SCAN_FROM, present, NOW.minusSeconds(60), NOW, 30);
        assertThat(plan.kind()).isEqualTo("TODAY");
        assertThat(plan.from()).isEqualTo(TODAY);
        assertThat(plan.to()).isEqualTo(TODAY);
    }

    @Test
    void okWhenTodayCoveredAndPollNotYetPersistedAfterRestart() {
        Set<LocalDate> present = allDaysInScanWindow();
        var plan = CatchUpAdvisor.resolveSyncRange(TODAY, SCAN_FROM, present, null, NOW, 30);
        assertThat(plan.kind()).isEqualTo("TODAY");
    }

    @Test
    void extendWithRecentDaysPullsYesterdayWhenPlanWasTodayOnly() {
        Set<LocalDate> present = allDaysInScanWindow();
        var base = CatchUpAdvisor.resolveSyncRange(
                TODAY, SCAN_FROM, present, NOW.minusSeconds(60), NOW, 30);
        assertThat(base.kind()).isEqualTo("TODAY");
        var extended = CatchUpAdvisor.extendWithRecentDays(base, TODAY, 2);
        assertThat(extended.kind()).isEqualTo("RECENT_REFRESH");
        assertThat(extended.from()).isEqualTo(TODAY.minusDays(1));
        assertThat(extended.to()).isEqualTo(TODAY);
    }

    @Test
    void legacyAdviceStillWorksForGap() {
        var advice = CatchUpAdvisor.of(TODAY, LocalDate.of(2026, 8, 12), NOW.minusSeconds(3600), NOW);
        assertThat(advice.needed()).isTrue();
        assertThat(advice.kind()).isEqualTo("GAP");
    }

    private static Set<LocalDate> allDaysInScanWindow() {
        java.util.HashSet<LocalDate> days = new java.util.HashSet<>();
        for (LocalDate d = SCAN_FROM; !d.isAfter(TODAY); d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }
}
