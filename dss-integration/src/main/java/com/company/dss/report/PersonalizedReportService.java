package com.company.dss.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
import com.company.dss.persistence.entity.SiteEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.persistence.repository.SiteRepository;
import com.company.dss.report.PersonalizedReportResponse.DailyBreakdownRow;
import com.company.dss.report.PersonalizedReportResponse.DailyDetailRow;
import com.company.dss.report.PersonalizedReportResponse.DailyEvolutionPoint;
import com.company.dss.report.PersonalizedReportResponse.HourlySlotAverageRow;
import com.company.dss.report.PersonalizedReportResponse.HourlySlotDetailRow;
import com.company.dss.report.PersonalizedReportResponse.ReportFilters;
import com.company.dss.report.PersonalizedReportResponse.ReportKpis;
import com.company.dss.report.PersonalizedReportResponse.SiteCountRow;
import com.company.dss.report.PersonalizedReportResponse.WeekdayDistributionRow;

import lombok.RequiredArgsConstructor;

/**
 * Agrège les créneaux horaires pour le rapport (filtres UI uniquement).
 * <p>
 * Ne interroge pas DSS : lit {@code people_counting_hourly} remplie par la sync / le poll.
 * Règle de comptage : un site = somme des entrées/sorties de ses caméras actives configurées.
 * « Tous » = tous les sites ayant au moins une caméra active (canal {@code $1$}).
 * Les caméras sans site ne sont comptées dans aucun rapport.
 */
@Service
@RequiredArgsConstructor
public class PersonalizedReportService {

    public static final int MAX_SITES = 20;
    public static final String ALL_SITES_LABEL = "Tous les sites";

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);
    private static final Locale FR = Locale.FRENCH;

    private final PeopleCountingHourlyRepository hourlyRepository;
    private final CameraRepository cameraRepository;
    private final SiteRepository siteRepository;

    public PersonalizedReportResponse build(
            LocalDate fromDate,
            LocalDate toDate,
            LocalTime timeFrom,
            LocalTime timeTo,
            String siteParam,
            String groupBy
    ) {
        SiteSelection selection = parseSiteSelection(siteParam);
        List<SiteRef> sites = resolveSites(selection);
        Map<String, Long> siteByChannel = new LinkedHashMap<>();
        for (SiteRef site : sites) {
            for (String channelId : site.channelIds()) {
                siteByChannel.put(channelId, site.id());
            }
        }
        List<String> channelIds = List.copyOf(siteByChannel.keySet());
        List<LocalTime> slotStarts = hourSlotsInRange(timeFrom, timeTo);
        Set<LocalTime> allowedHours = new HashSet<>(slotStarts);

        List<PeopleCountingHourlyEntity> rows = List.of();
        if (!channelIds.isEmpty() && !slotStarts.isEmpty()) {
            LocalTime lastHourStart = slotStarts.get(slotStarts.size() - 1);
            rows = hourlyRepository.findForReportByChannels(
                    fromDate, toDate, timeFrom, lastHourStart, channelIds
            );
        }

        Map<LocalDate, Map<LocalTime, Map<Long, long[]>>> byDayHourSite =
                aggregateByDayHourAndSite(rows, allowedHours, siteByChannel);

        BreakdownData breakdown = buildDailyBreakdown(
                fromDate, toDate, timeTo, slotStarts, sites, byDayHourSite
        );
        List<DailyBreakdownRow> daysNewestFirst = breakdown.dailyBreakdown();

        List<DailyDetailRow> details = new ArrayList<>();
        List<DailyEvolutionPoint> evolution = new ArrayList<>();
        long totalIn = 0;
        long totalOut = 0;
        for (DailyBreakdownRow day : daysNewestFirst) {
            details.add(new DailyDetailRow(
                    day.date(), day.entries(), day.exits(), day.presenceEndOfSlot()
            ));
            evolution.add(new DailyEvolutionPoint(day.date(), day.entries(), day.exits()));
            totalIn += day.entries();
            totalOut += day.exits();
        }

        long dayCount = daysNewestFirst.size();
        long netPresence = totalIn - totalOut;
        long avgPresence = dayCount == 0 ? 0 : Math.round((double) netPresence / dayCount);

        Map<DayOfWeek, long[]> byWeekday = new LinkedHashMap<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            byWeekday.put(dow, new long[2]);
        }
        for (DailyBreakdownRow day : daysNewestFirst) {
            long[] agg = byWeekday.get(day.date().getDayOfWeek());
            agg[0] += day.entries();
            agg[1] += day.exits();
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
                        selectionLabel(selection, sites),
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
                weekdays,
                daysNewestFirst,
                breakdown.hourlySlotAverages()
        );
    }

    /**
     * Somme entrées/sorties par jour, créneau et site ; chaque canal est compté dans le site de sa caméra.
     */
    static Map<LocalDate, Map<LocalTime, Map<Long, long[]>>> aggregateByDayHourAndSite(
            List<PeopleCountingHourlyEntity> rows,
            Set<LocalTime> allowedHours,
            Map<String, Long> siteByChannel
    ) {
        Map<LocalDate, Map<LocalTime, Map<Long, long[]>>> byDayHourSite = new TreeMap<>();
        for (PeopleCountingHourlyEntity row : rows) {
            LocalTime hour = row.getHourStart();
            if (allowedHours != null && !allowedHours.isEmpty() && !allowedHours.contains(hour)) {
                continue;
            }
            if (row.getCamera() == null || !StringUtils.hasText(row.getCamera().getChannelId())) {
                continue;
            }
            Long siteId = siteByChannel.get(row.getCamera().getChannelId());
            if (siteId == null) {
                continue;
            }
            Map<LocalTime, Map<Long, long[]>> byHour =
                    byDayHourSite.computeIfAbsent(row.getSlotDate(), d -> new TreeMap<>());
            Map<Long, long[]> bySite = byHour.computeIfAbsent(hour, h -> new LinkedHashMap<>());
            long[] agg = bySite.computeIfAbsent(siteId, id -> new long[2]);
            agg[0] += Math.max(0, row.getEntries());
            agg[1] += Math.max(0, row.getExits());
        }
        return byDayHourSite;
    }

    private BreakdownData buildDailyBreakdown(
            LocalDate fromDate,
            LocalDate toDate,
            LocalTime timeTo,
            List<LocalTime> slotStarts,
            List<SiteRef> sites,
            Map<LocalDate, Map<LocalTime, Map<Long, long[]>>> byDayHourSite
    ) {
        long calendarDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1L;
        Map<LocalTime, long[]> totalsByHour = new LinkedHashMap<>();
        Map<LocalTime, Map<Long, long[]>> totalsByHourSite = new LinkedHashMap<>();
        for (LocalTime slot : slotStarts) {
            totalsByHour.put(slot, new long[2]);
            totalsByHourSite.put(slot, new LinkedHashMap<>());
            for (SiteRef site : sites) {
                totalsByHourSite.get(slot).put(site.id(), new long[2]);
            }
        }

        List<DailyBreakdownRow> breakdownRows = new ArrayList<>();
        for (LocalDate day = toDate; !day.isBefore(fromDate); day = day.minusDays(1)) {
            Map<LocalTime, Map<Long, long[]>> dayHours = byDayHourSite.getOrDefault(day, Map.of());
            long dayIn = 0;
            long dayOut = 0;
            List<HourlySlotDetailRow> slots = new ArrayList<>();
            for (LocalTime slotStart : slotStarts) {
                List<SiteCountRow> siteRows = siteRowsForSlot(sites, dayHours.get(slotStart));
                long[] slotSum = sumSites(siteRows);
                long in = slotSum[0];
                long out = slotSum[1];
                dayIn += in;
                dayOut += out;
                long[] hourTotal = totalsByHour.get(slotStart);
                hourTotal[0] += in;
                hourTotal[1] += out;
                Map<Long, long[]> hourSiteTotal = totalsByHourSite.get(slotStart);
                for (SiteCountRow site : siteRows) {
                    long[] acc = hourSiteTotal.get(site.siteId());
                    if (acc != null) {
                        acc[0] += site.entries();
                        acc[1] += site.exits();
                    }
                }
                LocalTime slotEnd = slotEndLabel(slotStart, timeTo);
                slots.add(new HourlySlotDetailRow(
                        formatHour(slotStart),
                        formatHour(slotEnd),
                        in,
                        out,
                        in - out,
                        0,
                        0,
                        0,
                        siteRows
                ));
            }
            breakdownRows.add(new DailyBreakdownRow(day, dayIn, dayOut, dayIn - dayOut, slots));
        }

        List<HourlySlotAverageRow> averages = new ArrayList<>();
        for (LocalTime slotStart : slotStarts) {
            long[] total = totalsByHour.get(slotStart);
            long totalPresence = total[0] - total[1];
            LocalTime slotEnd = slotEndLabel(slotStart, timeTo);
            averages.add(new HourlySlotAverageRow(
                    formatHour(slotStart),
                    formatHour(slotEnd),
                    total[0],
                    total[1],
                    totalPresence,
                    round1Ratio(total[0], calendarDays),
                    round1Ratio(total[1], calendarDays),
                    round1Ratio(totalPresence, calendarDays),
                    siteRowsWithAverages(sites, totalsByHourSite.get(slotStart), calendarDays)
            ));
        }

        Map<String, HourlySlotAverageRow> avgByHour = new LinkedHashMap<>();
        for (HourlySlotAverageRow avg : averages) {
            avgByHour.put(avg.hourStart(), avg);
        }
        List<DailyBreakdownRow> withAverages = new ArrayList<>();
        for (DailyBreakdownRow dayRow : breakdownRows) {
            List<HourlySlotDetailRow> slotsWithAvg = new ArrayList<>();
            for (HourlySlotDetailRow slot : dayRow.hourlySlots()) {
                HourlySlotAverageRow avg = avgByHour.get(slot.hourStart());
                slotsWithAvg.add(new HourlySlotDetailRow(
                        slot.hourStart(),
                        slot.hourEnd(),
                        slot.entries(),
                        slot.exits(),
                        slot.presence(),
                        avg != null ? avg.averageEntries() : 0,
                        avg != null ? avg.averageExits() : 0,
                        avg != null ? avg.averagePresence() : 0,
                        withPeriodAverages(slot.sites(), avg != null ? avg.sites() : List.of())
                ));
            }
            withAverages.add(new DailyBreakdownRow(
                    dayRow.date(),
                    dayRow.entries(),
                    dayRow.exits(),
                    dayRow.presenceEndOfSlot(),
                    slotsWithAvg
            ));
        }

        return new BreakdownData(withAverages, averages);
    }

    private static List<SiteCountRow> siteRowsForSlot(List<SiteRef> sites, Map<Long, long[]> bySite) {
        Map<Long, long[]> src = bySite == null ? Map.of() : bySite;
        List<SiteCountRow> list = new ArrayList<>();
        for (SiteRef site : sites) {
            long[] v = src.getOrDefault(site.id(), new long[2]);
            long in = v[0];
            long out = v[1];
            list.add(new SiteCountRow(site.id(), site.name(), in, out, in - out));
        }
        return list;
    }

    /** Totaux de période par site, avec moyenne journalière sur les jours calendaires. */
    private static List<SiteCountRow> siteRowsWithAverages(
            List<SiteRef> sites,
            Map<Long, long[]> bySite,
            long calendarDays
    ) {
        Map<Long, long[]> src = bySite == null ? Map.of() : bySite;
        List<SiteCountRow> list = new ArrayList<>();
        for (SiteRef site : sites) {
            long[] v = src.getOrDefault(site.id(), new long[2]);
            long in = v[0];
            long out = v[1];
            list.add(new SiteCountRow(
                    site.id(),
                    site.name(),
                    in,
                    out,
                    in - out,
                    round1Ratio(in, calendarDays),
                    round1Ratio(out, calendarDays)
            ));
        }
        return list;
    }

    private static List<SiteCountRow> withPeriodAverages(
            List<SiteCountRow> dayRows,
            List<SiteCountRow> periodRows
    ) {
        Map<Long, SiteCountRow> byId = new LinkedHashMap<>();
        for (SiteCountRow row : periodRows) {
            byId.put(row.siteId(), row);
        }
        List<SiteCountRow> list = new ArrayList<>();
        for (SiteCountRow day : dayRows) {
            SiteCountRow period = byId.get(day.siteId());
            list.add(new SiteCountRow(
                    day.siteId(),
                    day.siteName(),
                    day.entries(),
                    day.exits(),
                    day.presence(),
                    period != null ? period.averageEntries() : 0,
                    period != null ? period.averageExits() : 0
            ));
        }
        return list;
    }

    private static long[] sumSites(List<SiteCountRow> sites) {
        long in = 0;
        long out = 0;
        for (SiteCountRow site : sites) {
            in += site.entries();
            out += site.exits();
        }
        return new long[]{in, out};
    }

    static List<LocalTime> hourSlotsInRange(LocalTime timeFrom, LocalTime timeTo) {
        List<LocalTime> slots = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            LocalTime start = LocalTime.of(h, 0);
            if (!start.isBefore(timeFrom) && isHourBeforeTimeTo(start, timeTo)) {
                slots.add(start);
            }
        }
        return slots;
    }

    private static boolean isHourBeforeTimeTo(LocalTime hourStart, LocalTime timeTo) {
        if (timeTo.equals(END_OF_DAY)) {
            return true;
        }
        return hourStart.isBefore(timeTo);
    }

    private static LocalTime slotEndLabel(LocalTime slotStart, LocalTime timeTo) {
        LocalTime next = slotStart.plusHours(1);
        if (next.equals(LocalTime.MIDNIGHT)) {
            return LocalTime.MIDNIGHT;
        }
        if (!isHourBeforeTimeTo(next, timeTo) && !timeTo.equals(END_OF_DAY)) {
            return timeTo;
        }
        return next;
    }

    private static String formatHour(LocalTime time) {
        if (time.equals(LocalTime.MIDNIGHT)) {
            return "00:00";
        }
        return HOUR_FMT.format(time);
    }

    /**
     * Arrondi à 1 décimale, half-up, à partir du rapport exact entier / jours.
     * Évite l'erreur binaire de {@code (double) n / jours}.
     */
    static double round1Ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record BreakdownData(
            List<DailyBreakdownRow> dailyBreakdown,
            List<HourlySlotAverageRow> hourlySlotAverages
    ) {
    }

    /**
     * Sites triés par nom avec leurs canaux actifs configurés.
     * « Tous » → sites ayant au moins une caméra active ; sinon les sites demandés (même sans caméra).
     */
    private List<SiteRef> resolveSites(SiteSelection selection) {
        Map<Long, List<CameraEntity>> camerasBySite = new LinkedHashMap<>();
        for (CameraEntity camera : cameraRepository.findAll()) {
            if (camera.getSite() == null || camera.getSite().getId() == null) {
                continue;
            }
            if (!CompteuseCameraRules.isConfigured(camera.getChannelId(), camera.isActive())) {
                continue;
            }
            camerasBySite.computeIfAbsent(camera.getSite().getId(), id -> new ArrayList<>()).add(camera);
        }

        List<SiteEntity> allSites = siteRepository.findAll().stream()
                .sorted(Comparator.comparing(SiteEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<SiteEntity> selected;
        if (selection.includeAll()) {
            selected = allSites.stream().filter(s -> camerasBySite.containsKey(s.getId())).toList();
        } else {
            Set<Long> wanted = new LinkedHashSet<>(selection.siteIds());
            selected = allSites.stream().filter(s -> wanted.contains(s.getId())).toList();
            if (selected.isEmpty()) {
                throw new IllegalArgumentException("Site introuvable : sélectionnez un site existant.");
            }
        }
        return selected.stream()
                .map(site -> new SiteRef(
                        site.getId(),
                        site.getName(),
                        camerasBySite.getOrDefault(site.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(
                                        c -> StringUtils.hasText(c.getName()) ? c.getName() : c.getChannelId(),
                                        String.CASE_INSENSITIVE_ORDER))
                                .map(CameraEntity::getChannelId)
                                .toList()
                ))
                .toList();
    }

    private static String selectionLabel(SiteSelection selection, List<SiteRef> sites) {
        if (selection.includeAll()) {
            return ALL_SITES_LABEL;
        }
        return String.join(", ", sites.stream().map(SiteRef::name).toList());
    }

    static SiteSelection parseSiteSelection(String siteParam) {
        if (!StringUtils.hasText(siteParam)) {
            throw new IllegalArgumentException("Sélectionnez au moins 1 site (ou « Tous »).");
        }
        String raw = siteParam.trim();
        if (isAllToken(raw)) {
            return new SiteSelection(true, List.of());
        }

        Set<Long> unique = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (!StringUtils.hasText(token) || isAllToken(token)) {
                continue;
            }
            try {
                unique.add(Long.parseLong(token));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Identifiant de site invalide : " + token);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins 1 site (ou « Tous »).");
        }
        if (unique.size() > MAX_SITES) {
            throw new IllegalArgumentException(
                    "Maximum " + MAX_SITES + " sites autorisés (reçu : " + unique.size() + ")."
            );
        }
        return new SiteSelection(false, List.copyOf(unique));
    }

    public static boolean isAllToken(String value) {
        return "all".equalsIgnoreCase(value)
                || "tous".equalsIgnoreCase(value)
                || "toutes".equalsIgnoreCase(value);
    }

    private static String capitalize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(FR) + value.substring(1);
    }

    record SiteRef(Long id, String name, List<String> channelIds) {
    }

    record SiteSelection(boolean includeAll, List<Long> siteIds) {
        SiteSelection {
            siteIds = siteIds == null ? List.of() : List.copyOf(siteIds);
        }
    }
}
