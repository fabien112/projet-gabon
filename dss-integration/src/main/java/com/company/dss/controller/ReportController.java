package com.company.dss.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.report.PersonalizedReportResponse;
import com.company.dss.report.PersonalizedReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final PersonalizedReportService reportService;
    private final CameraRepository cameraRepository;
    private final PeopleCountingHourlyRepository hourlyRepository;

    /**
     * Rapport personnalisé pour l'UI People Counting.
     * Tranche 09:00-11:00 = créneaux dont hour_start ∈ [09:00, 11:00).
     */
    @GetMapping("/personalized")
    public ResponseEntity<PersonalizedReportResponse> personalized(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "00:00") @DateTimeFormat(pattern = "HH:mm") LocalTime timeFrom,
            @RequestParam(defaultValue = "24:00") String timeTo,
            @RequestParam(defaultValue = "all") String camera,
            @RequestParam(defaultValue = "Jour") String groupBy
    ) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La date de fin doit être >= date de début");
        }
        LocalTime effectiveTo = resolveTimeTo(timeTo);
        if (!effectiveTo.equals(END_OF_DAY) && !effectiveTo.isAfter(timeFrom)) {
            throw new IllegalArgumentException("La tranche horaire est invalide (timeTo > timeFrom)");
        }
        return ResponseEntity.ok(reportService.build(from, to, timeFrom, effectiveTo, camera, groupBy));
    }

    @GetMapping("/cameras")
    public ResponseEntity<?> cameras() {
        return ResponseEntity.ok(cameraRepository.findAll().stream()
                .map(this::toCameraDto)
                .toList());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameras", cameraRepository.count());
        body.put("hourlySlots", hourlyRepository.count());
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> toCameraDto(CameraEntity camera) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", camera.getId());
        dto.put("channelId", camera.getChannelId());
        dto.put("name", camera.getName());
        dto.put("site", camera.getSite());
        dto.put("active", camera.isActive());
        return dto;
    }

    /** 24:00 / 23:59 → fin de journée (inclut le créneau 23:00). */
    private static LocalTime resolveTimeTo(String raw) {
        if ("24:00".equals(raw) || "23:59".equals(raw)) {
            return END_OF_DAY;
        }
        return LocalTime.parse(raw);
    }
}
