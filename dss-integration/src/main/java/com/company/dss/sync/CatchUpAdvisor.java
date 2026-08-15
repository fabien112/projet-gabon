package com.company.dss.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Détecte un trou de données après une coupure (sans interroger DSS).
 * La mise en service reste une sync manuelle de tout l'historique.
 */
public final class CatchUpAdvisor {

    static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private CatchUpAdvisor() {
    }

    public static Advice of(LocalDate today, LocalDate lastCovered, Instant lastPollAt, Instant now) {
        if (today == null) {
            return Advice.ok("Statut indisponible.");
        }
        if (lastCovered == null) {
            return new Advice(
                    true,
                    "EMPTY",
                    null,
                    today,
                    "Aucune donnée en base. À la mise en service, lancez une synchronisation de tout l’historique."
            );
        }
        if (lastCovered.isBefore(today)) {
            long days = ChronoUnit.DAYS.between(lastCovered, today);
            return new Advice(
                    true,
                    "GAP",
                    lastCovered,
                    today,
                    "Le logiciel a été coupé. Données jusqu’au "
                            + lastCovered
                            + " — " + days + " jour(s) à rattraper jusqu’à aujourd’hui."
            );
        }
        if (lastPollAt != null && now != null && Duration.between(lastPollAt, now).compareTo(STALE_AFTER) > 0) {
            return new Advice(
                    true,
                    "STALE_TODAY",
                    today,
                    today,
                    "Coupure pendant la journée en cours. La journée peut être incomplète — un rattrapage d’aujourd’hui suffit."
            );
        }
        return Advice.ok("À jour — dernier jour en base : aujourd’hui. Le poll continue tout seul.");
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
