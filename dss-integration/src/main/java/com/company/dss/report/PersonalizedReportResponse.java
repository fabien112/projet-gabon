package com.company.dss.report;

import java.time.LocalDate;
import java.util.List;

public record PersonalizedReportResponse(
        ReportFilters filters,
        ReportKpis kpis,
        List<DailyDetailRow> dailyDetails,
        List<DailyEvolutionPoint> dailyEvolution,
        List<WeekdayDistributionRow> weekdayDistribution
) {
    public record ReportFilters(
            LocalDate fromDate,
            LocalDate toDate,
            String timeFrom,
            String timeTo,
            String camera,
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
}
