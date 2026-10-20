package com.company.dss.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class VerifiedCursorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    @Test
    void nothingPendingWhenCursorIsYesterday() {
        var range = VerifiedCursor.pending(TODAY, TODAY.minusDays(1), 30);
        assertThat(range.isEmpty()).isTrue();
    }

    @Test
    void downtimeFillsFromCursorPlusOneThroughYesterday() {
        LocalDate verified = LocalDate.of(2026, 9, 18);
        var range = VerifiedCursor.pending(TODAY, verified, 30);
        assertThat(range.isEmpty()).isFalse();
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    void firstRunWithoutCursorUsesWindowUntilYesterday() {
        var range = VerifiedCursor.pending(TODAY, null, 30);
        assertThat(range.from()).isEqualTo(TODAY.minusDays(30));
        assertThat(range.to()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void halfDayDoesNotAdvanceCursorToToday() {
        LocalDate verified = LocalDate.of(2026, 9, 21);
        assertThat(VerifiedCursor.advance(verified, TODAY, TODAY)).isEqualTo(verified);
    }

    @Test
    void successfulClosedDayAdvancesCursor() {
        LocalDate yesterday = TODAY.minusDays(1);
        assertThat(VerifiedCursor.advance(TODAY.minusDays(2), yesterday, TODAY)).isEqualTo(yesterday);
    }
}
