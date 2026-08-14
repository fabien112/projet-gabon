package com.company.dss.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.camera.CameraConfigService;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.report.PersonalizedReportResponse;
import com.company.dss.report.PersonalizedReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);
    private static final int MAX_CAMERAS = 50;

    private final PersonalizedReportService reportService;
    private final CameraConfigService cameraConfigService;
    private final PeopleCountingHourlyRepository hourlyRepository;

    /**
     * Rapport personnalisé pour l'UI People Counting.
     * Tranche 09:00-11:00 = créneaux dont hour_start ∈ [09:00, 11:00).
     * <p>
     * {@code camera} : {@code all}, un channelId, ou jusqu'à 50 ids séparés par des virgules.
     */
    @GetMapping("/personalized")
    public ResponseEntity<PersonalizedReportResponse> personalized(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "00:00") @DateTimeFormat(pattern = "HH:mm") LocalTime timeFrom,
            @RequestParam(defaultValue = "24:00") String timeTo,
            @RequestParam(defaultValue = "all") List<String> camera,
            @RequestParam(defaultValue = "Jour") String groupBy
    ) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La date de fin doit être >= date de début");
        }
        LocalTime effectiveTo = resolveTimeTo(timeTo);
        if (!effectiveTo.equals(END_OF_DAY) && !effectiveTo.isAfter(timeFrom)) {
            throw new IllegalArgumentException("La tranche horaire est invalide (timeTo > timeFrom)");
        }
        String cameraParam = normalizeCameraParam(camera);
        return ResponseEntity.ok(reportService.build(from, to, timeFrom, effectiveTo, cameraParam, groupBy));
    }

    /**
     * Normalise {@code camera=all}, {@code camera=id}, {@code camera=id1,id2}
     * ou params répétés {@code camera=id1&camera=id2} en une seule chaîne CSV (max 50).
     */
    public static String normalizeCameraParam(List<String> camera) {
        if (camera == null || camera.isEmpty()) {
            return "all";
        }
        Set<String> ids = new LinkedHashSet<>();
        boolean all = false;
        for (String raw : camera) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            for (String part : raw.split(",")) {
                String id = part.trim();
                if (!StringUtils.hasText(id)) {
                    continue;
                }
                if ("all".equalsIgnoreCase(id) || "toutes".equalsIgnoreCase(id)) {
                    all = true;
                } else {
                    ids.add(id);
                }
            }
        }
        if (all && ids.isEmpty()) {
            return "all";
        }
        if (ids.isEmpty()) {
            return "all";
        }
        if (ids.size() > MAX_CAMERAS) {
            throw new IllegalArgumentException(
                    "Maximum " + MAX_CAMERAS + " caméras autorisées (reçu : " + ids.size() + ")."
            );
        }
        return String.join(",", ids);
    }

    /**
     * Caméras de comptage configurées (canaux {@code $1$}),
     * sans les doublons techniques {@code $3$} / {@code *_1}.
     */
    @GetMapping("/cameras")
    public ResponseEntity<?> cameras() {
        return ResponseEntity.ok(cameraConfigService.listConfigured());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameras", cameraConfigService.listConfigured().size());
        body.put("hourlySlots", hourlyRepository.count());
        return ResponseEntity.ok(body);
    }

    /** 24:00 / 23:59 → fin de journée (inclut le créneau 23:00). */
    private static LocalTime resolveTimeTo(String raw) {
        if ("24:00".equals(raw) || "23:59".equals(raw)) {
            return END_OF_DAY;
        }
        return LocalTime.parse(raw);
    }
}
