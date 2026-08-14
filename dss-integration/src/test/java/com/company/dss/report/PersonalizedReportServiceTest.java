package com.company.dss.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.dss.controller.ReportController;
import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.entity.PeopleCountingHourlyEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;

@ExtendWith(MockitoExtension.class)
class PersonalizedReportServiceTest {

    @Mock
    private PeopleCountingHourlyRepository hourlyRepository;

    @Mock
    private CameraRepository cameraRepository;

    private PersonalizedReportService service;

    @BeforeEach
    void setUp() {
        service = new PersonalizedReportService(hourlyRepository, cameraRepository);
    }

    @Test
    void parseCameraSelection_dedupesAndCapsAtFifty() {
        var sel = PersonalizedReportService.parseCameraSelection("a,b,a,c");
        assertThat(sel.includeAll()).isFalse();
        assertThat(sel.channelIds()).containsExactly("a", "b", "c");
    }

    @Test
    void parseCameraSelection_rejectsMoreThanFifty() {
        String tooMany = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "cam-" + i)
                .collect(java.util.stream.Collectors.joining(","));
        assertThatThrownBy(() -> PersonalizedReportService.parseCameraSelection(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum 50");
    }

    @Test
    void parseCameraSelection_all() {
        var sel = PersonalizedReportService.parseCameraSelection("all");
        assertThat(sel.includeAll()).isTrue();
        assertThat(sel.channelIds()).isEmpty();
    }

    @Test
    void build_sumsOnlySelectedCamerasWithoutDoubleCounting() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        when(cameraRepository.findAll()).thenReturn(List.of(
                primary("cam-A", "Compteuse A"),
                primary("cam-B", "Compteuse B")
        ));
        when(hourlyRepository.findForReportByChannels(
                eq(day), eq(day), eq(LocalTime.of(9, 0)), eq(LocalTime.of(11, 0)), any()
        )).thenReturn(List.of(
                slot("cam-A", day, LocalTime.of(9, 0), 10, 3),
                slot("cam-B", day, LocalTime.of(9, 0), 5, 2),
                slot("cam-A", day, LocalTime.of(10, 0), 7, 4),
                slot("cam-B", day, LocalTime.of(10, 0), 1, 1)
        ));

        var report = service.build(
                day, day, LocalTime.of(9, 0), LocalTime.of(11, 0), "cam-A,cam-B", "Jour"
        );

        assertThat(report.kpis().totalEntries()).isEqualTo(23);
        assertThat(report.kpis().totalExits()).isEqualTo(10);
        assertThat(report.kpis().netPresence()).isEqualTo(13);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> idsCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(hourlyRepository).findForReportByChannels(
                eq(day), eq(day), eq(LocalTime.of(9, 0)), eq(LocalTime.of(11, 0)),
                idsCaptor.capture()
        );
        assertThat(idsCaptor.getValue()).containsExactly("cam-A", "cam-B");
    }

    @Test
    void build_allUsesOnlyPrimaryCameras() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        when(cameraRepository.findAll()).thenReturn(List.of(
                primary("1000004$1$0$0", "Cam_Compteuse_Entree_Akanda"),
                primary("1000005$1$0$0", "Cam_Compteuse_Entree_Oloumi"),
                primary("1000005$1$0$1", "Cam_Compteuse_Sortie_Oloumi"),
                // doublon technique — exclu
                camera("1000004$3$1$0", "Cam_Compteuse_Entree_Akanda_1", true)
        ));
        when(hourlyRepository.findForReportByChannels(any(), any(), any(), any(), any()))
                .thenReturn(List.of(slot("1000004$1$0$0", day, LocalTime.of(9, 0), 4, 1)));

        var report = service.build(
                day, day, LocalTime.of(9, 0), LocalTime.of(11, 0), "all", "Jour"
        );

        assertThat(report.kpis().totalEntries()).isEqualTo(4);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> idsCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(hourlyRepository).findForReportByChannels(any(), any(), any(), any(), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).hasSize(3);
        assertThat(idsCaptor.getValue()).noneMatch(id -> id.contains("$3$"));
    }

    @Test
    void build_allIncludesManuallyAddedCameras() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        when(cameraRepository.findAll()).thenReturn(List.of(
                primary("1000004$1$0$0", "Cam_Compteuse_Entree_Akanda"),
                camera("1000009$1$0$0", "Hall principal", true)
        ));
        when(hourlyRepository.findForReportByChannels(any(), any(), any(), any(), any()))
                .thenReturn(List.of(slot("1000009$1$0$0", day, LocalTime.of(9, 0), 8, 2)));

        service.build(day, day, LocalTime.of(9, 0), LocalTime.of(11, 0), "all", "Jour");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> idsCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(hourlyRepository).findForReportByChannels(any(), any(), any(), any(), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(
                "1000004$1$0$0",
                "1000009$1$0$0"
        );
    }

    @Test
    void normalizeCameraParam_joinsRepeatedAndCsv() {
        assertThat(ReportController.normalizeCameraParam(List.of("a", "b,c")))
                .isEqualTo("a,b,c");
        assertThat(ReportController.normalizeCameraParam(List.of("all")))
                .isEqualTo("all");
    }

    private static CameraEntity primary(String channelId, String name) {
        return camera(channelId, name, true);
    }

    private static CameraEntity camera(String channelId, String name, boolean active) {
        CameraEntity cam = new CameraEntity();
        cam.setChannelId(channelId);
        cam.setName(name);
        cam.setActive(active);
        return cam;
    }

    private static PeopleCountingHourlyEntity slot(
            String channelId, LocalDate date, LocalTime hour, int entries, int exits
    ) {
        CameraEntity cam = primary(channelId, channelId);
        PeopleCountingHourlyEntity h = new PeopleCountingHourlyEntity();
        h.setCamera(cam);
        h.setSlotDate(date);
        h.setHourStart(hour);
        h.setHourEnd(hour.plusHours(1));
        h.setEntries(entries);
        h.setExits(exits);
        return h;
    }
}
