package com.company.dss.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.company.dss.passengerflow.CompteuseChannel;
import com.company.dss.passengerflow.PassengerFlowClient;
import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.entity.PeopleCountingHourlyEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Upsert des créneaux horaires DSS → MySQL/H2 pour le rapport personnalisé.
 * Chargement groupé (caméras + créneaux du jour) : pas de N+1, pas d'UPDATE si inchangé.
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
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        List<ParsedSlot> parsed = new ArrayList<>();
        Set<String> channelIds = new LinkedHashSet<>();
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            ParsedSlot slot = parseSlot(row);
            if (slot == null) {
                continue;
            }
            parsed.add(slot);
            channelIds.add(slot.channelId());
            dates.add(slot.slotDate());
        }
        if (parsed.isEmpty()) {
            return 0;
        }

        Map<String, CameraEntity> cameras = loadOrCreateCameras(parsed, channelIds);
        List<Long> cameraIds = cameras.values().stream()
                .map(CameraEntity::getId)
                .filter(id -> id != null)
                .toList();
        if (cameraIds.isEmpty()) {
            return 0;
        }

        Map<String, PeopleCountingHourlyEntity> existing = new HashMap<>();
        for (PeopleCountingHourlyEntity entity : hourlyRepository.findForUpsert(dates, cameraIds)) {
            existing.put(slotKey(entity.getCamera().getId(), entity.getSlotDate(), entity.getHourStart()), entity);
        }

        List<PeopleCountingHourlyEntity> toSave = new ArrayList<>();
        for (ParsedSlot slot : parsed) {
            CameraEntity camera = cameras.get(slot.channelId());
            if (camera == null || camera.getId() == null) {
                continue;
            }
            String key = slotKey(camera.getId(), slot.slotDate(), slot.hourStart());
            PeopleCountingHourlyEntity entity = existing.get(key);
            if (entity == null) {
                entity = new PeopleCountingHourlyEntity();
                entity.setCamera(camera);
                entity.setSlotDate(slot.slotDate());
                entity.setHourStart(slot.hourStart());
                entity.setHourEnd(slot.hourEnd());
                entity.setEntries(slot.entries());
                entity.setExits(slot.exits());
                entity.setOccupancy(slot.occupancy());
                existing.put(key, entity);
                toSave.add(entity);
            } else if (needsUpdate(entity, slot)) {
                entity.setHourEnd(slot.hourEnd());
                entity.setEntries(slot.entries());
                entity.setExits(slot.exits());
                entity.setOccupancy(slot.occupancy());
                toSave.add(entity);
            }
        }
        if (!toSave.isEmpty()) {
            hourlyRepository.saveAll(toSave);
        }
        return toSave.size();
    }

    /**
     * Enregistre / met à jour toutes les caméras compteuses découvertes (même sans données du jour).
     */
    @Transactional
    public int upsertCameras(List<CompteuseChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (CompteuseChannel channel : channels) {
            if (!StringUtils.hasText(channel.channelId())) {
                continue;
            }
            CameraEntity camera = cameraRepository.findByChannelId(channel.channelId()).orElseGet(() -> {
                CameraEntity created = new CameraEntity();
                created.setChannelId(channel.channelId());
                created.setActive(true);
                return created;
            });
            String name = StringUtils.hasText(channel.name()) ? channel.name() : channel.channelId();
            if (!camera.isManual()) {
                camera.setName(name);
                camera.setSite(guessSite(name));
            } else if (!StringUtils.hasText(camera.getName())) {
                camera.setName(name);
            }
            camera.setActive(true);
            cameraRepository.save(camera);
            saved++;
        }
        return saved;
    }

    private Map<String, CameraEntity> loadOrCreateCameras(List<ParsedSlot> parsed, Collection<String> channelIds) {
        Map<String, CameraEntity> cameras = new LinkedHashMap<>();
        for (CameraEntity camera : cameraRepository.findByChannelIdIn(channelIds)) {
            cameras.put(camera.getChannelId(), camera);
        }

        Map<String, String> dssNames = new LinkedHashMap<>();
        for (ParsedSlot slot : parsed) {
            dssNames.putIfAbsent(slot.channelId(), slot.cameraName());
        }

        List<CameraEntity> camerasToSave = new ArrayList<>();
        for (String channelId : channelIds) {
            CameraEntity camera = cameras.get(channelId);
            String dssName = dssNames.get(channelId);
            if (camera == null) {
                camera = new CameraEntity();
                camera.setChannelId(channelId);
                camera.setName(StringUtils.hasText(dssName) ? dssName : channelId);
                camera.setSite(guessSite(dssName));
                camera.setActive(true);
                cameras.put(channelId, camera);
                camerasToSave.add(camera);
            } else if (!camera.isManual()
                    && StringUtils.hasText(dssName)
                    && !dssName.equals(camera.getName())) {
                camera.setName(dssName);
                camera.setSite(guessSite(dssName));
                camerasToSave.add(camera);
            }
        }
        if (!camerasToSave.isEmpty()) {
            cameraRepository.saveAll(camerasToSave);
        }
        return cameras;
    }

    private static boolean needsUpdate(PeopleCountingHourlyEntity entity, ParsedSlot slot) {
        return entity.getEntries() != slot.entries()
                || entity.getExits() != slot.exits()
                || entity.getOccupancy() != slot.occupancy()
                || !slot.hourEnd().equals(entity.getHourEnd());
    }

    private static String slotKey(Long cameraId, LocalDate date, LocalTime hourStart) {
        return cameraId + "|" + date + "|" + hourStart;
    }

    private static ParsedSlot parseSlot(Map<String, Object> row) {
        String channelId = stringVal(row.get("channelId"));
        String cameraName = stringVal(row.get("camera"));
        String startTime = stringVal(row.get("startTime"));
        String endTime = stringVal(row.get("endTime"));
        if (!StringUtils.hasText(channelId) || !StringUtils.hasText(startTime) || !StringUtils.hasText(endTime)) {
            return null;
        }
        if (!PassengerFlowClient.isMainVideoChannelCode(channelId)) {
            return null;
        }
        LocalDateTime start = LocalDateTime.parse(startTime, DSS_DT);
        LocalDateTime end = LocalDateTime.parse(endTime, DSS_DT);
        LocalDate slotDate = start.toLocalDate();
        LocalTime hourStart = start.toLocalTime();
        LocalTime hourEnd = end.toLocalTime();
        if (end.toLocalDate().isAfter(slotDate) && hourEnd.equals(LocalTime.MIDNIGHT)) {
            hourEnd = LocalTime.MIDNIGHT;
        }
        return new ParsedSlot(
                channelId,
                cameraName,
                slotDate,
                hourStart,
                hourEnd,
                intVal(row.get("in")),
                intVal(row.get("out")),
                intVal(row.get("occupancy"))
        );
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

    private record ParsedSlot(
            String channelId,
            String cameraName,
            LocalDate slotDate,
            LocalTime hourStart,
            LocalTime hourEnd,
            int entries,
            int exits,
            int occupancy
    ) {
    }
}
