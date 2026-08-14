package com.company.dss.report;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.company.dss.passengerflow.CompteuseCameraRules;
import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.entity.PeopleCountingHourlyEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.report.PersonalizedReportResponse.DailyDetailRow;
import com.company.dss.report.PersonalizedReportResponse.DailyEvolutionPoint;
import com.company.dss.report.PersonalizedReportResponse.ReportFilters;
import com.company.dss.report.PersonalizedReportResponse.ReportKpis;
import com.company.dss.report.PersonalizedReportResponse.WeekdayDistributionRow;

import lombok.RequiredArgsConstructor;

/**
 * Agrège les créneaux horaires pour le rapport.
 * <p>
 * Règle de comptage : somme des entrées/sorties des caméras sélectionnées uniquement.
 * « Toutes » = toutes les caméras actives configurées (canaux {@code $1$}), jamais les doublons {@code $3$}.
 */
@Service
@RequiredArgsConstructor
public class PersonalizedReportService {

    public static final int MAX_CAMERAS = 50;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale FR = Locale.FRENCH;

    private final PeopleCountingHourlyRepository hourlyRepository;
    private final CameraRepository cameraRepository;

    public PersonalizedReportResponse build(
            LocalDate fromDate,
            LocalDate toDate,
            LocalTime timeFrom,
            LocalTime timeTo,
            String cameraParam,
            String groupBy
    ) {
        CameraSelection selection = parseCameraSelection(cameraParam);
        List<String> channelIds = resolveChannelIds(selection);

        List<PeopleCountingHourlyEntity> rows = channelIds.isEmpty()
                ? List.of()
                : hourlyRepository.findForReportByChannels(
                        fromDate, toDate, timeFrom, timeTo, channelIds
                );

        // Agrégation journalière — une seule passe.
        // Présence = Σin − Σout (= Σ(in−out) par caméra). Créneau unique en DB.
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (PeopleCountingHourlyEntity row : rows) {
            long[] agg = byDay.computeIfAbsent(row.getSlotDate(), d -> new long[2]);
            agg[0] += Math.max(0, row.getEntries());
            agg[1] += Math.max(0, row.getExits());
        }

        List<DailyDetailRow> details = new ArrayList<>();
        List<DailyEvolutionPoint> evolution = new ArrayList<>();
        long totalIn = 0;
        long totalOut = 0;
        for (Map.Entry<LocalDate, long[]> e : byDay.entrySet()) {
            long in = e.getValue()[0];
            long out = e.getValue()[1];
            details.add(new DailyDetailRow(e.getKey(), in, out, in - out));
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

        String periodLabel = DAY_FMT.format(fromDate) + " → " + DAY_FMT.format(toDate)
                + "\n" + timeFrom + " → " + timeTo;

        return new PersonalizedReportResponse(
                new ReportFilters(
                        fromDate,
                        toDate,
                        timeFrom.toString(),
                        timeTo.toString(),
                        selection.label(),
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

    /** « Toutes » → ids des caméras configurées ; sinon les ids demandés (filtrés). */
    private List<String> resolveChannelIds(CameraSelection selection) {
        List<String> primary = primaryChannelIds();
        if (selection.includeAll()) {
            return primary;
        }
        Set<String> primarySet = new LinkedHashSet<>(primary);
        return selection.channelIds().stream()
                .filter(primarySet::contains)
                .limit(MAX_CAMERAS)
                .toList();
    }

    private List<String> primaryChannelIds() {
        return cameraRepository.findAll().stream()
                .filter(c -> CompteuseCameraRules.isPrimary(c.getChannelId(), c.getName(), c.isActive()))
                .sorted(Comparator.comparing(CameraEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(CameraEntity::getChannelId)
                .limit(MAX_CAMERAS)
                .toList();
    }

    static CameraSelection parseCameraSelection(String cameraParam) {
        if (!StringUtils.hasText(cameraParam)) {
            throw new IllegalArgumentException("Sélectionnez au moins 1 caméra (ou « Toutes »).");
        }
        String raw = cameraParam.trim();
        if ("all".equalsIgnoreCase(raw) || "toutes".equalsIgnoreCase(raw)) {
            return new CameraSelection(true, List.of(), "Toutes");
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (StringUtils.hasText(id) && !"all".equalsIgnoreCase(id) && !"toutes".equalsIgnoreCase(id)) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins 1 caméra (ou « Toutes »).");
        }
        if (unique.size() > MAX_CAMERAS) {
            throw new IllegalArgumentException(
                    "Maximum " + MAX_CAMERAS + " caméras autorisées (reçu : " + unique.size() + ")."
            );
        }
        List<String> ids = List.copyOf(unique);
        String label = ids.size() == 1 ? ids.get(0) : ids.get(0) + " +" + (ids.size() - 1);
        return new CameraSelection(false, ids, label);
    }

    private static String capitalize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(FR) + value.substring(1);
    }

    record CameraSelection(boolean includeAll, List<String> channelIds, String label) {
        CameraSelection {
            channelIds = channelIds == null ? List.of() : List.copyOf(channelIds);
        }
    }
}
