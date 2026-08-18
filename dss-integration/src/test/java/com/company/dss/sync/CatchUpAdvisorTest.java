package com.company.dss.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CatchUpAdvisorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

    @Test
    void emptyDatabaseSyncsInitialWindow() {
        var plan = CatchUpAdvisor.resolveSyncRange(TODAY, null, null, NOW, 30);
        assertThat(plan.kind()).isEqualTo("EMPTY");
        assertThat(plan.from()).isEqualTo(TODAY.minusDays(29));
        assertThat(plan.to()).isEqualTo(TODAY);
    }

    @Test
    void gapAfterDowntimeCoversLastDayThroughToday() {
        var plan = CatchUpAdvisor.resolveSyncRange(
                TODAY, LocalDate.of(2026, 8, 12), NOW.minusSeconds(3600), NOW, 30);
        assertThat(plan.kind()).isEqualTo("GAP");
        assertThat(plan.from()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(plan.to()).isEqualTo(TODAY);
    }

    @Test
    void staleTodayResyncsToday() {
        var plan = CatchUpAdvisor.resolveSyncRange(
                TODAY, TODAY, NOW.minusSeconds(20 * 60), NOW, 30);
        assertThat(plan.kind()).isEqualTo("STALE_TODAY");
        assertThat(plan.from()).isEqualTo(TODAY);
        assertThat(plan.to()).isEqualTo(TODAY);
    }

    @Test
    void okWhenTodayCoveredSyncsTodayForMissingSlots() {
        var plan = CatchUpAdvisor.resolveSyncRange(
                TODAY, TODAY, NOW.minusSeconds(60), NOW, 30);
        assertThat(plan.kind()).isEqualTo("TODAY");
        assertThat(plan.from()).isEqualTo(TODAY);
        assertThat(plan.to()).isEqualTo(TODAY);
    }

    @Test
    void okWhenTodayCoveredAndPollNotYetPersistedAfterRestart() {
        var plan = CatchUpAdvisor.resolveSyncRange(TODAY, TODAY, null, NOW, 30);
        assertThat(plan.kind()).isEqualTo("TODAY");
    }

    @Test
    void legacyAdviceStillWorksForGap() {
        var advice = CatchUpAdvisor.of(TODAY, LocalDate.of(2026, 8, 12), NOW.minusSeconds(3600), NOW);
        assertThat(advice.needed()).isTrue();
        assertThat(advice.kind()).isEqualTo("GAP");
    }
}
