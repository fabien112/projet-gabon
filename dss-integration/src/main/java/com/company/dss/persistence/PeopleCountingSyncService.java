package com.company.dss.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.entity.PeopleCountingHourlyEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Upsert des créneaux horaires DSS → MySQL/H2 pour le rapport personnalisé.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeopleCountingSyncService {

    private static final DateTimeFormatter DSS_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CameraRepository cameraRepository;
    private final PeopleCountingHourlyRepository hourlyRepository;

    @Transactional
    public int upsertRows(List<Map<String, Object>> rows) {
        int saved = 0;
        for (Map<String, Object> row : rows) {
            if (upsertOne(row)) {
                saved++;
            }
        }
        return saved;
    }

    private boolean upsertOne(Map<String, Object> row) {
        String channelId = stringVal(row.get("channelId"));
        String cameraName = stringVal(row.get("camera"));
        String startTime = stringVal(row.get("startTime"));
        String endTime = stringVal(row.get("endTime"));
        if (!StringUtils.hasText(channelId) || !StringUtils.hasText(startTime) || !StringUtils.hasText(endTime)) {
            return false;
        }

        LocalDateTime start = LocalDateTime.parse(startTime, DSS_DT);
        LocalDateTime end = LocalDateTime.parse(endTime, DSS_DT);
        LocalDate slotDate = start.toLocalDate();
        LocalTime hourStart = start.toLocalTime();
        LocalTime hourEnd = end.toLocalTime();
        // créneau qui passe minuit (23:00 → 00:00)
        if (end.toLocalDate().isAfter(slotDate) && hourEnd.equals(LocalTime.MIDNIGHT)) {
            hourEnd = LocalTime.MIDNIGHT;
        }

        CameraEntity camera = cameraRepository.findByChannelId(channelId).orElseGet(() -> {
            CameraEntity created = new CameraEntity();
            created.setChannelId(channelId);
            created.setName(StringUtils.hasText(cameraName) ? cameraName : channelId);
            created.setSite(guessSite(cameraName));
            created.setActive(true);
            return cameraRepository.save(created);
        });
        if (StringUtils.hasText(cameraName) && !cameraName.equals(camera.getName())) {
            camera.setName(cameraName);
            camera.setSite(guessSite(cameraName));
            cameraRepository.save(camera);
        }

        int entries = intVal(row.get("in"));
        int exits = intVal(row.get("out"));
        int occupancy = intVal(row.get("occupancy"));

        PeopleCountingHourlyEntity entity = hourlyRepository
                .findByCameraIdAndSlotDateAndHourStart(camera.getId(), slotDate, hourStart)
                .orElseGet(PeopleCountingHourlyEntity::new);
        entity.setCamera(camera);
        entity.setSlotDate(slotDate);
        entity.setHourStart(hourStart);
        entity.setHourEnd(hourEnd);
        entity.setEntries(entries);
        entity.setExits(exits);
        entity.setOccupancy(occupancy);
        hourlyRepository.save(entity);
        return true;
    }

    private static String guessSite(String cameraName) {
        if (!StringUtils.hasText(cameraName)) {
            return null;
        }
        String upper = cameraName.toUpperCase(Locale.ROOT);
        if (upper.contains("AKANDA")) {
            return "Akanda";
        }
        if (upper.contains("OLOUMI")) {
            return "Oloumi";
        }
        return null;
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intVal(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
