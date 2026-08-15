package com.company.dss.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CatchUpAdvisorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

    @Test
    void emptyDatabaseRequiresCommissioningSync() {
        var advice = CatchUpAdvisor.of(TODAY, null, null, NOW);
        assertThat(advice.needed()).isTrue();
        assertThat(advice.kind()).isEqualTo("EMPTY");
        assertThat(advice.from()).isNull();
    }

    @Test
    void gapAfterDowntimeCoversLastDayThroughToday() {
        var advice = CatchUpAdvisor.of(TODAY, LocalDate.of(2026, 8, 12), NOW.minusSeconds(3600), NOW);
        assertThat(advice.needed()).isTrue();
        assertThat(advice.kind()).isEqualTo("GAP");
        assertThat(advice.from()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(advice.to()).isEqualTo(TODAY);
        assertThat(advice.message()).contains("3 jour");
    }

    @Test
    void staleTodayWhenPollOlderThan15Minutes() {
        var advice = CatchUpAdvisor.of(TODAY, TODAY, NOW.minusSeconds(20 * 60), NOW);
        assertThat(advice.needed()).isTrue();
        assertThat(advice.kind()).isEqualTo("STALE_TODAY");
        assertThat(advice.from()).isEqualTo(TODAY);
        assertThat(advice.to()).isEqualTo(TODAY);
    }

    @Test
    void okWhenTodayCoveredAndPollFresh() {
        var advice = CatchUpAdvisor.of(TODAY, TODAY, NOW.minusSeconds(60), NOW);
        assertThat(advice.needed()).isFalse();
        assertThat(advice.kind()).isEqualTo("OK");
    }

    @Test
    void okWhenTodayCoveredAndPollNotYetPersistedAfterRestart() {
        var advice = CatchUpAdvisor.of(TODAY, TODAY, null, NOW);
        assertThat(advice.needed()).isFalse();
    }
}
