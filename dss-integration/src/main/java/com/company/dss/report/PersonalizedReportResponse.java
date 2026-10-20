package com.company.dss.report;

import java.time.LocalDate;
import java.util.List;

public record PersonalizedReportResponse(
        ReportFilters filters,
        ReportKpis kpis,
        List<DailyDetailRow> dailyDetails,
        List<DailyEvolutionPoint> dailyEvolution,
        List<WeekdayDistributionRow> weekdayDistribution,
        List<DailyBreakdownRow> dailyBreakdown,
        List<HourlySlotAverageRow> hourlySlotAverages
) {
    public record ReportFilters(
            LocalDate fromDate,
            LocalDate toDate,
            String timeFrom,
            String timeTo,
            String site,
            String groupBy
    ) {
    }

    public record ReportKpis(
            long totalEntries,
            long totalExits,
            long netPresence,
            long averageDailyPresence,
            long dayCount,
            String periodLabel
    ) {
    }

    public record DailyDetailRow(
            LocalDate date,
            long entries,
            long exits,
            long presenceEndOfSlot
    ) {
    }

    public record DailyEvolutionPoint(
            LocalDate date,
            long entries,
            long exits
    ) {
    }

    public record WeekdayDistributionRow(
            String weekday,
            int weekdayIndex,
            long entries,
            long exits
    ) {
    }

    /** Tous les jours calendaires de la période, avec détail par créneau horaire. */
    public record DailyBreakdownRow(
            LocalDate date,
            long entries,
            long exits,
            long presenceEndOfSlot,
            List<HourlySlotDetailRow> hourlySlots
    ) {
    }

    public record HourlySlotDetailRow(
            String hourStart,
            String hourEnd,
            long entries,
            long exits,
            long presence,
            double averageEntries,
            double averageExits,
            double averagePresence,
            List<SiteCountRow> sites
    ) {
        public HourlySlotDetailRow {
            sites = sites == null ? List.of() : List.copyOf(sites);
        }
    }

    /** Totaux d'un site (somme de ses caméras) sur une tranche (jour) ou sur la période (global). */
    public record SiteCountRow(
            Long siteId,
            String siteName,
            long entries,
            long exits,
            long presence,
            double averageEntries,
            double averageExits
    ) {
        public SiteCountRow(
                Long siteId,
                String siteName,
                long entries,
                long exits,
                long presence
        ) {
            this(siteId, siteName, entries, exits, presence, 0, 0);
        }
    }

    /** Moyennes par tranche sur l'ensemble des jours calendaires de la période. */
    public record HourlySlotAverageRow(
            String hourStart,
            String hourEnd,
            long totalEntries,
            long totalExits,
            long totalPresence,
            double averageEntries,
            double averageExits,
            double averagePresence,
            List<SiteCountRow> sites
    ) {
        public HourlySlotAverageRow {
            sites = sites == null ? List.of() : List.copyOf(sites);
        }
    }
}
