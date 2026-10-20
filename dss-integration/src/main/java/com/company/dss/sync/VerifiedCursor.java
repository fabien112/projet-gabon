package com.company.dss.sync;

import java.time.LocalDate;

/**
 * Plage de jours clos à réaligner sur DSS : après le curseur, jusqu'à hier.
 * Les jours {@code <= verifiedThrough} sont considérés bons et ne sont plus relus.
 */
public final class VerifiedCursor {

    private VerifiedCursor() {
    }

    public record Range(LocalDate from, LocalDate to) {
        public boolean isEmpty() {
            return from == null || to == null || to.isBefore(from);
        }
    }

    /**
     * @param windowDays import initial si aucun curseur (base vide ou 1er déploiement)
     */
    public static Range pending(LocalDate today, LocalDate verifiedThrough, int windowDays) {
        if (today == null) {
            return new Range(null, null);
        }
        LocalDate yesterday = today.minusDays(1);
        int window = Math.max(1, windowDays);
        LocalDate from;
        if (verifiedThrough != null) {
            from = verifiedThrough.plusDays(1);
        } else {
            from = yesterday.minusDays(window - 1L);
        }
        if (from.isAfter(yesterday)) {
            return new Range(null, null);
        }
        return new Range(from, yesterday);
    }

    public static LocalDate advance(LocalDate currentVerified, LocalDate syncedDay, LocalDate today) {
        if (syncedDay == null || today == null || !syncedDay.isBefore(today)) {
            return currentVerified;
        }
        if (currentVerified == null || syncedDay.isAfter(currentVerified)) {
            return syncedDay;
        }
        return currentVerified;
    }
}
