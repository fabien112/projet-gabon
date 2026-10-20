package com.company.dss.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.passengerflow.CompteuseCameraRules;
import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.entity.SiteEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.persistence.repository.SiteRepository;
import com.company.dss.report.PersonalizedReportResponse;
import com.company.dss.report.PersonalizedReportService;
import com.company.dss.report.ReportEventService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);
    private static final int MAX_SITES = PersonalizedReportService.MAX_SITES;

    private final PersonalizedReportService reportService;
    private final CameraRepository cameraRepository;
    private final SiteRepository siteRepository;
    private final PeopleCountingHourlyRepository hourlyRepository;
    private final ReportEventService reportEventService;

    /**
     * Rapport personnalisé pour l'UI People Counting.
     * Tranche 09:00-11:00 = créneaux 09:00 et 10:00 (hour_start inclusif jusqu'au dernier créneau).
     * <p>
     * {@code site} : {@code all}, un id de site, ou plusieurs ids séparés par des virgules.
     * Chaque site agrège toutes ses caméras actives.
     */
    @GetMapping("/personalized")
    public ResponseEntity<PersonalizedReportResponse> personalized(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "00:00") @DateTimeFormat(pattern = "HH:mm") LocalTime timeFrom,
            @RequestParam(defaultValue = "24:00") String timeTo,
            @RequestParam(defaultValue = "all") List<String> site,
            @RequestParam(defaultValue = "Jour") String groupBy
    ) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La date de fin doit être >= date de début");
        }
        LocalTime effectiveTo = resolveTimeTo(timeTo);
        if (!effectiveTo.equals(END_OF_DAY) && !effectiveTo.isAfter(timeFrom)) {
            throw new IllegalArgumentException("La tranche horaire est invalide (timeTo > timeFrom)");
        }
        String siteParam = normalizeSiteParam(site);
        return ResponseEntity.ok(reportService.build(from, to, timeFrom, effectiveTo, siteParam, groupBy));
    }

    /**
     * Normalise {@code site=all}, {@code site=id}, {@code site=id1,id2}
     * ou params répétés {@code site=id1&site=id2} en une seule chaîne CSV.
     */
    public static String normalizeSiteParam(List<String> site) {
        if (site == null || site.isEmpty()) {
            return "all";
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String raw : site) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            for (String part : raw.split(",")) {
                String id = part.trim();
                if (StringUtils.hasText(id) && !PersonalizedReportService.isAllToken(id)) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return "all";
        }
        if (ids.size() > MAX_SITES) {
            throw new IllegalArgumentException(
                    "Maximum " + MAX_SITES + " sites autorisés (reçu : " + ids.size() + ")."
            );
        }
        return String.join(",", ids);
    }

    /**
     * Sites sélectionnables dans le rapport : ceux qui ont au moins une caméra active configurée.
     */
    @GetMapping("/sites")
    public ResponseEntity<List<Map<String, Object>>> sites() {
        Map<Long, List<String>> camerasBySite = activeCameraNamesBySite();
        return ResponseEntity.ok(siteRepository.findAll().stream()
                .filter(s -> camerasBySite.containsKey(s.getId()))
                .sorted(Comparator.comparing(SiteEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", s.getId());
                    dto.put("name", s.getName());
                    dto.put("cameras", camerasBySite.get(s.getId()));
                    return dto;
                })
                .toList());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<Long, List<String>> camerasBySite = activeCameraNamesBySite();
        body.put("sites", camerasBySite.size());
        body.put("cameras", camerasBySite.values().stream().mapToInt(List::size).sum());
        body.put("hourlySlots", hourlyRepository.count());
        hourlyRepository.findMinSlotDate().ifPresent(d -> body.put("minSlotDate", d));
        hourlyRepository.findMaxSlotDate().ifPresent(d -> body.put("maxSlotDate", d));
        return ResponseEntity.ok(body);
    }

    /** Flux SSE : le frontend recharge le rapport quand de nouvelles données arrivent en base. */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reportEvents() {
        return reportEventService.subscribe();
    }

    private Map<Long, List<String>> activeCameraNamesBySite() {
        Map<Long, List<String>> bySite = new LinkedHashMap<>();
        cameraRepository.findAll().stream()
                .filter(c -> c.getSite() != null && c.getSite().getId() != null)
                .filter(c -> CompteuseCameraRules.isConfigured(c.getChannelId(), c.isActive()))
                .sorted(Comparator.comparing(CameraEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(c -> bySite.computeIfAbsent(c.getSite().getId(), id -> new ArrayList<>()).add(c.getName()));
        return bySite;
    }

    /** 24:00 / 23:59 → fin de journée (inclut le créneau 23:00). */
    private static LocalTime resolveTimeTo(String raw) {
        if ("24:00".equals(raw) || "23:59".equals(raw)) {
            return END_OF_DAY;
        }
        return LocalTime.parse(raw);
    }
}
