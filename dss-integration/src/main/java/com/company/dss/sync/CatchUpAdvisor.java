package com.company.dss.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

/**
 * Calcule la période à synchroniser pour combler les trous en base (sans interroger DSS).
 */
public final class CatchUpAdvisor {

    static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private CatchUpAdvisor() {
    }

    /**
     * @deprecated Préférer {@link #resolveSyncRange(LocalDate, LocalDate, Set, Instant, Instant, int)} avec couverture réelle.
     */
    @Deprecated
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
        if (lastCovered == null) {
            return emptyInitialPlan(today, initialSyncDays);
        }
        Set<LocalDate> contiguous = new HashSet<>();
        for (LocalDate d = lastCovered; !d.isAfter(today); d = d.plusDays(1)) {
            contiguous.add(d);
        }
        LocalDate scanFrom = today.minusDays(Math.max(1, initialSyncDays) - 1L);
        if (lastCovered.isBefore(scanFrom)) {
            scanFrom = lastCovered;
        }
        return resolveSyncRange(today, scanFrom, contiguous, lastPollAt, now, initialSyncDays);
    }

    /**
     * Période à synchroniser en analysant chaque jour calendaire de {@code scanFrom} à {@code today}.
     */
    public static SyncPlan resolveSyncRange(
            LocalDate today,
            LocalDate scanFrom,
            Set<LocalDate> datesPresent,
            Instant lastPollAt,
            Instant now,
            int initialSyncDays
    ) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }
        int days = Math.max(1, initialSyncDays);
        Set<LocalDate> present = datesPresent != null ? datesPresent : Set.of();

        if (present.isEmpty()) {
            return emptyInitialPlan(today, days);
        }

        LocalDate from = scanFrom != null ? scanFrom : today.minusDays(days - 1L);
        if (from.isAfter(today)) {
            from = today;
        }

        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            if (!present.contains(d)) {
                long missingDays = ChronoUnit.DAYS.between(d, today) + 1;
                return new SyncPlan(
                        d,
                        today,
                        "GAP",
                        missingDays + " jour(s) manquant(s) (" + d + " → " + today + ")"
                );
            }
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

    /**
     * Élargit la plage pour re-télécharger les derniers jours calendaires depuis DSS
     * (une date « présente » en base peut être incomplète après une coupure).
     */
    public static SyncPlan extendWithRecentDays(SyncPlan plan, LocalDate today, int recentCalendarDays) {
        if (plan == null || today == null) {
            return plan;
        }
        int n = Math.max(1, recentCalendarDays);
        LocalDate refreshFrom = today.minusDays(n - 1L);
        LocalDate from = plan.from().isBefore(refreshFrom) ? plan.from() : refreshFrom;
        LocalDate to = plan.to().isAfter(today) ? plan.to() : today;
        if (from.equals(plan.from()) && to.equals(plan.to())) {
            return plan;
        }
        String msg = plan.message() + " — rafraîchissement récent " + from + " → " + to;
        return new SyncPlan(from, to, "RECENT_REFRESH", msg);
    }

    private static SyncPlan emptyInitialPlan(LocalDate today, int initialSyncDays) {
        int days = Math.max(1, initialSyncDays);
        LocalDate from = today.minusDays(days - 1L);
        return new SyncPlan(
                from,
                today,
                "EMPTY",
                "Import initial — " + days + " jour(s) (" + from + " → " + today + ")"
        );
    }

    /** @deprecated conservé pour compatibilité statut — préférer {@link #resolveSyncRange} avec couverture. */
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
