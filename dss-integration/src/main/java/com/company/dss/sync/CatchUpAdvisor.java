package com.company.dss.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Calcule la période à synchroniser pour combler les trous en base (sans interroger DSS).
 */
public final class CatchUpAdvisor {

    static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private CatchUpAdvisor() {
    }

    /**
     * Période à synchroniser pour récupérer tout ce qui manque en base.
     */
    public static SyncPlan resolveSyncRange(
            LocalDate today,
            LocalDate lastCovered,
            Instant lastPollAt,
            Instant now,
            int initialSyncDays
    ) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }
        int days = Math.max(1, initialSyncDays);
        if (lastCovered == null) {
            LocalDate from = today.minusDays(days - 1L);
            return new SyncPlan(
                    from,
                    today,
                    "EMPTY",
                    "Import initial — " + days + " jour(s) (" + from + " → " + today + ")"
            );
        }
        if (lastCovered.isBefore(today)) {
            long gapDays = ChronoUnit.DAYS.between(lastCovered, today);
            return new SyncPlan(
                    lastCovered,
                    today,
                    "GAP",
                    gapDays + " jour(s) à récupérer (" + lastCovered + " → " + today + ")"
            );
        }
        if (lastPollAt != null && now != null && Duration.between(lastPollAt, now).compareTo(STALE_AFTER) > 0) {
            return new SyncPlan(
                    today,
                    today,
                    "STALE_TODAY",
                    "Journée en cours à compléter (" + today + ")"
            );
        }
        return new SyncPlan(
                today,
                today,
                "TODAY",
                "Compléter les créneaux manquants du " + today
        );
    }

    /** @deprecated conservé pour compatibilité statut — préférer {@link #resolveSyncRange}. */
    public static Advice of(LocalDate today, LocalDate lastCovered, Instant lastPollAt, Instant now) {
        if (today == null) {
            return Advice.ok("Statut indisponible.");
        }
        SyncPlan plan = resolveSyncRange(today, lastCovered, lastPollAt, now, 30);
        boolean needed = !"TODAY".equals(plan.kind()) || isStaleToday(lastPollAt, now);
        if ("TODAY".equals(plan.kind()) && !needed) {
            return Advice.ok("Dernier jour en base : " + today + ".");
        }
        return new Advice(true, plan.kind(), plan.from(), plan.to(), plan.message());
    }

    private static boolean isStaleToday(Instant lastPollAt, Instant now) {
        return lastPollAt != null
                && now != null
                && Duration.between(lastPollAt, now).compareTo(STALE_AFTER) > 0;
    }

    public record SyncPlan(
            LocalDate from,
            LocalDate to,
            String kind,
            String message
    ) {
    }

    public record Advice(
            boolean needed,
            String kind,
            LocalDate from,
            LocalDate to,
            String message
    ) {
        static Advice ok(String message) {
            return new Advice(false, "OK", null, null, message);
        }
    }
}
