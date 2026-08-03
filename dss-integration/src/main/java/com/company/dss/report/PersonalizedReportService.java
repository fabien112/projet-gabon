package com.company.dss.report;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.company.dss.persistence.entity.PeopleCountingHourlyEntity;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.report.PersonalizedReportResponse.DailyDetailRow;
import com.company.dss.report.PersonalizedReportResponse.DailyEvolutionPoint;
import com.company.dss.report.PersonalizedReportResponse.ReportFilters;
import com.company.dss.report.PersonalizedReportResponse.ReportKpis;
import com.company.dss.report.PersonalizedReportResponse.WeekdayDistributionRow;

import lombok.RequiredArgsConstructor;

/**
 * Agrège les créneaux horaires stockés pour le rapport personnalisé de l'UI.
 */
@Service
@RequiredArgsConstructor
public class PersonalizedReportService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale FR = Locale.FRENCH;

    private final PeopleCountingHourlyRepository hourlyRepository;

    public PersonalizedReportResponse build(
            LocalDate fromDate,
            LocalDate toDate,
            LocalTime timeFrom,
            LocalTime timeTo,
            String channelIdOrAll,
            String groupBy
    ) {
        String channelId = normalizeChannel(channelIdOrAll);
        List<PeopleCountingHourlyEntity> rows = hourlyRepository.findForReport(
                fromDate, toDate, timeFrom, timeTo, channelId
        );

        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (PeopleCountingHourlyEntity row : rows) {
            long[] agg = byDay.computeIfAbsent(row.getSlotDate(), d -> new long[2]);
            agg[0] += row.getEntries();
            agg[1] += row.getExits();
        }

        List<DailyDetailRow> details = new ArrayList<>();
        List<DailyEvolutionPoint> evolution = new ArrayList<>();
        long totalIn = 0;
        long totalOut = 0;
        for (Map.Entry<LocalDate, long[]> e : byDay.entrySet()) {
            long in = e.getValue()[0];
            long out = e.getValue()[1];
            long presence = in - out;
            details.add(new DailyDetailRow(e.getKey(), in, out, presence));
            evolution.add(new DailyEvolutionPoint(e.getKey(), in, out));
            totalIn += in;
            totalOut += out;
        }

        long dayCount = byDay.size();
        long netPresence = totalIn - totalOut;
        long avgPresence = dayCount == 0 ? 0 : Math.round((double) netPresence / dayCount);

        Map<DayOfWeek, long[]> byWeekday = new LinkedHashMap<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            byWeekday.put(dow, new long[2]);
        }
        for (Map.Entry<LocalDate, long[]> e : byDay.entrySet()) {
            long[] agg = byWeekday.get(e.getKey().getDayOfWeek());
            agg[0] += e.getValue()[0];
            agg[1] += e.getValue()[1];
        }
        List<WeekdayDistributionRow> weekdays = byWeekday.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().getValue()))
                .map(e -> new WeekdayDistributionRow(
                        capitalize(e.getKey().getDisplayName(TextStyle.SHORT, FR)),
                        e.getKey().getValue(),
                        e.getValue()[0],
                        e.getValue()[1]
                ))
                .toList();

        String cameraLabel = channelId == null ? "Toutes" : channelId;
        String periodLabel = DAY_FMT.format(fromDate) + " → " + DAY_FMT.format(toDate)
                + "\n" + timeFrom + " → " + timeTo;

        return new PersonalizedReportResponse(
                new ReportFilters(
                        fromDate,
                        toDate,
                        timeFrom.toString(),
                        timeTo.toString(),
                        cameraLabel,
                        StringUtils.hasText(groupBy) ? groupBy : "Jour"
                ),
                new ReportKpis(
                        totalIn,
                        totalOut,
                        netPresence,
                        avgPresence,
                        dayCount,
                        periodLabel
                ),
                details,
                evolution,
                weekdays
        );
    }

    private static String normalizeChannel(String channelIdOrAll) {
        if (!StringUtils.hasText(channelIdOrAll)) {
            return null;
        }
        String v = channelIdOrAll.trim();
        if ("all".equalsIgnoreCase(v) || "toutes".equalsIgnoreCase(v)) {
            return null;
        }
        return v;
    }

    private static String capitalize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(FR) + value.substring(1);
    }
}
