package com.company.dss.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.entity.PeopleCountingHourlyEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;

@ExtendWith(MockitoExtension.class)
class PeopleCountingSyncServiceTest {

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private PeopleCountingHourlyRepository hourlyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PeopleCountingSyncService service;

    @BeforeEach
    void setUp() {
        service = new PeopleCountingSyncService(cameraRepository, hourlyRepository, eventPublisher);
    }

    @Test
    void upsertRows_skipsUnchangedSlots() {
        CameraEntity camera = camera(4L, "1000004$1$0$0", "Entree", false);
        PeopleCountingHourlyEntity existing = slot(camera, LocalDate.of(2026, 8, 15), LocalTime.of(10, 0), 12, 3, 9);

        when(cameraRepository.findByChannelIdIn(anyCollection())).thenReturn(List.of(camera));
        when(hourlyRepository.findForUpsert(anyCollection(), anyCollection())).thenReturn(List.of(existing));

        int saved = service.upsertRows(List.of(dssRow("1000004$1$0$0", "Entree", "2026-08-15 10:00:00", "2026-08-15 11:00:00", 12, 3, 9)));

        assertThat(saved).isZero();
        verify(hourlyRepository, never()).saveAll(any());
        verify(hourlyRepository, never()).save(any());
    }

    @Test
    void upsertRows_writesOnlyChangedValues() {
        CameraEntity camera = camera(4L, "1000004$1$0$0", "Entree", false);
        PeopleCountingHourlyEntity existing = slot(camera, LocalDate.of(2026, 8, 15), LocalTime.of(10, 0), 12, 3, 9);

        when(cameraRepository.findByChannelIdIn(anyCollection())).thenReturn(List.of(camera));
        when(hourlyRepository.findForUpsert(anyCollection(), anyCollection())).thenReturn(List.of(existing));
        when(hourlyRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        int saved = service.upsertRows(List.of(dssRow("1000004$1$0$0", "Entree", "2026-08-15 10:00:00", "2026-08-15 11:00:00", 20, 4, 16)));

        assertThat(saved).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PeopleCountingHourlyEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(hourlyRepository).saveAll(captor.capture());
        PeopleCountingHourlyEntity updated = captor.getValue().get(0);
        assertThat(updated.getEntries()).isEqualTo(20);
        assertThat(updated.getExits()).isEqualTo(4);
        assertThat(updated.getOccupancy()).isEqualTo(16);
    }

    @Test
    void upsertRows_doesNotRenameManualCamera() {
        CameraEntity camera = camera(4L, "1000004$1$0$0", "Nom perso", true);
        when(cameraRepository.findByChannelIdIn(anyCollection())).thenReturn(List.of(camera));
        when(hourlyRepository.findForUpsert(anyCollection(), anyCollection())).thenReturn(List.of());
        when(hourlyRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upsertRows(List.of(dssRow("1000004$1$0$0", "Nom DSS", "2026-08-15 10:00:00", "2026-08-15 11:00:00", 1, 0, 1)));

        assertThat(camera.getName()).isEqualTo("Nom perso");
        verify(cameraRepository, never()).saveAll(any());
    }

    private static CameraEntity camera(Long id, String channelId, String name, boolean manual) {
        CameraEntity camera = new CameraEntity();
        camera.setId(id);
        camera.setChannelId(channelId);
        camera.setName(name);
        camera.setManual(manual);
        camera.setActive(true);
        return camera;
    }

    private static PeopleCountingHourlyEntity slot(
            CameraEntity camera, LocalDate date, LocalTime hour, int in, int out, int occupancy
    ) {
        PeopleCountingHourlyEntity entity = new PeopleCountingHourlyEntity();
        entity.setId(99L);
        entity.setCamera(camera);
        entity.setSlotDate(date);
        entity.setHourStart(hour);
        entity.setHourEnd(hour.plusHours(1));
        entity.setEntries(in);
        entity.setExits(out);
        entity.setOccupancy(occupancy);
        return entity;
    }

    private static Map<String, Object> dssRow(
            String channelId, String name, String start, String end, int in, int out, int occupancy
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("channelId", channelId);
        row.put("camera", name);
        row.put("startTime", start);
        row.put("endTime", end);
        row.put("in", in);
        row.put("out", out);
        row.put("total", in + out);
        row.put("occupancy", occupancy);
        return row;
    }
}
