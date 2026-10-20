package com.company.dss.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import com.company.dss.controller.ReportController;
import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.entity.PeopleCountingHourlyEntity;
import com.company.dss.persistence.entity.SiteEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.persistence.repository.SiteRepository;

@ExtendWith(MockitoExtension.class)
class PersonalizedReportServiceTest {

    private static final SiteEntity AKANDA = site(1L, "Akanda");
    private static final SiteEntity OLOUMI = site(2L, "Oloumi");

    @Mock
    private PeopleCountingHourlyRepository hourlyRepository;

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private SiteRepository siteRepository;

    private PersonalizedReportService service;

    @BeforeEach
    void setUp() {
        service = new PersonalizedReportService(hourlyRepository, cameraRepository, siteRepository);
    }

    @Test
    void parseSiteSelection_dedupesAndKeepsOrder() {
        var sel = PersonalizedReportService.parseSiteSelection("2,1,2");
        assertThat(sel.includeAll()).isFalse();
        assertThat(sel.siteIds()).containsExactly(2L, 1L);
    }

    @Test
    void parseSiteSelection_rejectsMoreThanMax() {
        String ids = String.join(",", java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(String::valueOf).toList());
        assertThatThrownBy(() -> PersonalizedReportService.parseSiteSelection(ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum 20");
    }

    @Test
    void parseSiteSelection_rejectsNonNumericId() {
        assertThatThrownBy(() -> PersonalizedReportService.parseSiteSelection("1000001$1$0$0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Identifiant de site invalide");
    }

    @Test
    void parseSiteSelection_all() {
        assertThat(PersonalizedReportService.parseSiteSelection("all").includeAll()).isTrue();
        assertThat(PersonalizedReportService.parseSiteSelection("Tous").includeAll()).isTrue();
        assertThat(PersonalizedReportService.parseSiteSelection("all").siteIds()).isEmpty();
    }

    @Test
    void build_siteSumsAllItsCamerasWithoutDoubleCounting() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        givenSites(AKANDA, OLOUMI);
        when(cameraRepository.findAll()).thenReturn(List.of(
                primary("1000004$1$0$0", "Cam_Compteuse_Entree_Akanda", AKANDA),
                primary("1000005$1$0$0", "Cam_Compteuse_Entree_Oloumi", OLOUMI),
                primary("1000005$1$0$1", "Cam_Compteuse_Sortie_Oloumi", OLOUMI)
        ));
        when(hourlyRepository.findForReportByChannels(
                eq(day), eq(day), eq(LocalTime.of(9, 0)), eq(LocalTime.of(10, 0)), any()
        )).thenReturn(List.of(
                slot("1000005$1$0$0", day, LocalTime.of(9, 0), 10, 3),
                slot("1000005$1$0$1", day, LocalTime.of(9, 0), 5, 2),
                slot("1000005$1$0$0", day, LocalTime.of(10, 0), 7, 4),
                slot("1000005$1$0$1", day, LocalTime.of(10, 0), 1, 1)
        ));

        var report = service.build(day, day, LocalTime.of(9, 0), LocalTime.of(11, 0), "2", "Jour");

        assertThat(report.filters().site()).isEqualTo("Oloumi");
        assertThat(report.kpis().totalEntries()).isEqualTo(23);
        assertThat(report.kpis().totalExits()).isEqualTo(10);
        assertThat(report.kpis().netPresence()).isEqualTo(13);
        var slot9 = report.dailyBreakdown().get(0).hourlySlots().get(0);
        assertThat(slot9.sites()).extracting(s -> s.siteName()).containsExactly("Oloumi");
        assertThat(slot9.sites()).extracting(s -> s.entries()).containsExactly(15L);
        assertThat(slot9.sites()).extracting(s -> s.exits()).containsExactly(5L);
        assertThat(slot9.entries()).isEqualTo(15);
        assertSurfacesConcord(report);

        assertThat(capturedChannelIds()).containsExactlyInAnyOrder("1000005$1$0$0", "1000005$1$0$1");
    }

    @Test
    void build_allUsesSitesWithActiveCamerasAndIgnoresUnlinkedCameras() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        SiteEntity empty = site(3L, "Sans caméra");
        givenSites(AKANDA, OLOUMI, empty);
        when(cameraRepository.findAll()).thenReturn(List.of(
                primary("1000004$1$0$0", "Cam_Compteuse_Entree_Akanda", AKANDA),
                primary("1000005$1$0$0", "Cam_Compteuse_Entree_Oloumi", OLOUMI),
                primary("1000005$1$0$1", "Cam_Compteuse_Sortie_Oloumi", OLOUMI),
                camera("1000006$1$0$0", "Entree Parking", OLOUMI, true),
                camera("1000007$1$0$0", "Inactive", AKANDA, false),
                camera("1000008$1$0$0", "Sans site", null, true),
                // doublon technique — exclu
                camera("1000004$3$1$0", "Cam_Compteuse_Entree_Akanda_1", AKANDA, true)
        ));
        when(hourlyRepository.findForReportByChannels(any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        slot("1000004$1$0$0", day, LocalTime.of(9, 0), 4, 1),
                        slot("1000006$1$0$0", day, LocalTime.of(9, 0), 2, 0)
                ));

        var report = service.build(day, day, LocalTime.of(9, 0), LocalTime.of(11, 0), "all", "Jour");
        assertSurfacesConcord(report);

        assertThat(report.filters().site()).isEqualTo("Tous les sites");
        assertThat(report.kpis().totalEntries()).isEqualTo(6);
        var slot9 = report.dailyBreakdown().get(0).hourlySlots().get(0);
        assertThat(slot9.sites()).extracting(s -> s.siteName()).containsExactly("Akanda", "Oloumi");
        assertThat(slot9.sites()).extracting(s -> s.entries()).containsExactly(4L, 2L);
        assertThat(capturedChannelIds()).containsExactlyInAnyOrder(
                "1000004$1$0$0", "1000005$1$0$0", "1000005$1$0$1", "1000006$1$0$0"
        );
    }

    @Test
    void build_explicitSiteWithoutCameraReturnsZerosWithoutQuerying() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        SiteEntity empty = site(3L, "Sans caméra");
        givenSites(AKANDA, empty);
        when(cameraRepository.findAll()).thenReturn(List.of(
                primary("1000004$1$0$0", "Cam_Compteuse_Entree_Akanda", AKANDA)
        ));

        var report = service.build(day, day, LocalTime.of(9, 0), LocalTime.of(10, 0), "3", "Jour");

        assertThat(report.kpis().totalEntries()).isZero();
        assertThat(report.hourlySlotAverages().get(0).sites())
                .extracting(s -> s.siteName()).containsExactly("Sans caméra");
        verify(hourlyRepository, never()).findForReportByChannels(any(), any(), any(), any(), any());
        assertSurfacesConcord(report);
    }

    @Test
    void build_rejectsUnknownSite() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        givenSites(AKANDA);
        when(cameraRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.build(day, day, LocalTime.of(9, 0), LocalTime.of(10, 0), "99", "Jour"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Site introuvable");
    }

    @Test
    void build_dailyBreakdown_listsAllCalendarDaysWithHourlyAverages() {
        LocalDate d1 = LocalDate.of(2026, 7, 27);
        LocalDate d2 = LocalDate.of(2026, 7, 28);
        givenSingleSiteCamera();
        when(hourlyRepository.findForReportByChannels(
                eq(d1), eq(d2), eq(LocalTime.of(9, 0)), eq(LocalTime.of(10, 0)), any()
        )).thenReturn(List.of(
                slot("1000001$1$0$0", d1, LocalTime.of(9, 0), 10, 2),
                slot("1000001$1$0$0", d1, LocalTime.of(10, 0), 4, 1),
                slot("1000001$1$0$0", d2, LocalTime.of(9, 0), 6, 0)
        ));

        var report = service.build(d1, d2, LocalTime.of(9, 0), LocalTime.of(11, 0), "1", "Jour");

        assertThat(report.dailyBreakdown()).hasSize(2);
        assertThat(report.dailyBreakdown().get(0).date()).isEqualTo(d2);
        assertThat(report.dailyBreakdown().get(0).entries()).isEqualTo(6);
        assertThat(report.dailyBreakdown().get(1).date()).isEqualTo(d1);
        assertThat(report.dailyBreakdown().get(1).entries()).isEqualTo(14);
        assertThat(report.dailyBreakdown().get(1).hourlySlots()).hasSize(2);
        assertThat(report.dailyBreakdown().get(1).hourlySlots().get(1).entries()).isEqualTo(4);

        assertThat(report.kpis().totalEntries()).isEqualTo(20);
        assertThat(report.dailyDetails().get(0).entries()).isEqualTo(6);

        assertThat(report.hourlySlotAverages()).hasSize(2);
        assertThat(report.hourlySlotAverages().get(0).averageEntries()).isEqualTo(8.0);
        assertThat(report.hourlySlotAverages().get(1).averageEntries()).isEqualTo(2.0);
        assertSurfacesConcord(report);
    }

    @Test
    void build_kpisMatchSumOfDailyBreakdownAcrossCalendarDays() {
        LocalDate d1 = LocalDate.of(2026, 7, 27);
        LocalDate d2 = LocalDate.of(2026, 7, 28);
        givenSingleSiteCamera();
        when(hourlyRepository.findForReportByChannels(
                eq(d1), eq(d2), eq(LocalTime.of(9, 0)), eq(LocalTime.of(10, 0)), any()
        )).thenReturn(List.of(
                slot("1000001$1$0$0", d1, LocalTime.of(9, 0), 10, 2)
        ));

        var report = service.build(d1, d2, LocalTime.of(9, 0), LocalTime.of(11, 0), "1", "Jour");

        long sumIn = report.dailyBreakdown().stream().mapToLong(r -> r.entries()).sum();
        long sumOut = report.dailyBreakdown().stream().mapToLong(r -> r.exits()).sum();
        assertThat(sumIn).isEqualTo(report.kpis().totalEntries());
        assertThat(sumOut).isEqualTo(report.kpis().totalExits());
        assertThat(report.dailyBreakdown().get(0).date()).isEqualTo(d2);
        assertThat(report.dailyBreakdown().get(0).entries()).isZero();
        assertThat(report.dailyBreakdown().get(1).entries()).isEqualTo(10);
        assertSurfacesConcord(report);
    }

    @Test
    void build_breakdownDayTotalsMatchHourlySlotSums() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        givenSingleSiteCamera();
        when(hourlyRepository.findForReportByChannels(any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        slot("1000001$1$0$0", day, LocalTime.of(9, 0), 10, 2),
                        slot("1000001$1$0$0", day, LocalTime.of(10, 0), 4, 1)
                ));

        var report = service.build(day, day, LocalTime.of(9, 0), LocalTime.of(11, 0), "1", "Jour");

        var breakdown = report.dailyBreakdown().get(0);
        long sumIn = breakdown.hourlySlots().stream().mapToLong(s -> s.entries()).sum();
        long sumOut = breakdown.hourlySlots().stream().mapToLong(s -> s.exits()).sum();
        assertThat(sumIn).isEqualTo(breakdown.entries());
        assertThat(sumOut).isEqualTo(breakdown.exits());
        assertThat(breakdown.entries()).isEqualTo(report.dailyDetails().get(0).entries());
        assertSurfacesConcord(report);
    }

    @Test
    void build_globalAndDetailTotalsStayIdenticalWithSeveralSitesAndDays() {
        LocalDate d1 = LocalDate.of(2026, 9, 20);
        LocalDate d2 = LocalDate.of(2026, 9, 21);
        LocalTime endOfDay = LocalTime.of(23, 59, 59);
        givenSites(AKANDA, OLOUMI);
        when(cameraRepository.findAll()).thenReturn(List.of(
                primary("1000004$1$0$0", "A", AKANDA),
                primary("1000005$1$0$0", "B", OLOUMI),
                primary("1000005$1$0$1", "C", OLOUMI)
        ));
        when(hourlyRepository.findForReportByChannels(
                eq(d1), eq(d2), eq(LocalTime.MIDNIGHT), eq(LocalTime.of(23, 0)), any()
        )).thenReturn(List.of(
                slot("1000004$1$0$0", d1, LocalTime.of(8, 0), 12, 4),
                slot("1000005$1$0$0", d1, LocalTime.of(8, 0), 5, 1),
                slot("1000005$1$0$1", d1, LocalTime.of(8, 0), 2, 6),
                slot("1000004$1$0$0", d1, LocalTime.of(15, 0), 20, 9),
                slot("1000005$1$0$0", d2, LocalTime.of(10, 0), 7, 2),
                slot("1000004$1$0$0", d2, LocalTime.of(23, 0), 3, 8)
        ));

        var report = service.build(d1, d2, LocalTime.MIDNIGHT, endOfDay, "all", "Jour");

        assertThat(report.dailyBreakdown()).hasSize(2);
        assertThat(report.dailyBreakdown().get(0).hourlySlots()).hasSize(24);
        assertSurfacesConcord(report);
        assertThat(report.kpis().totalEntries()).isEqualTo(12 + 5 + 2 + 20 + 7 + 3);
        assertThat(report.kpis().totalExits()).isEqualTo(4 + 1 + 6 + 9 + 2 + 8);
        assertThat(report.kpis().dayCount()).isEqualTo(2);
        assertThat(report.kpis().averageDailyPresence()).isEqualTo(
                Math.round(report.kpis().netPresence() / 2.0)
        );
        var slot8 = report.dailyBreakdown().get(1).hourlySlots().get(8);
        assertThat(slot8.sites()).extracting(s -> s.entries()).containsExactly(12L, 7L);
        assertThat(slot8.sites()).extracting(s -> s.exits()).containsExactly(4L, 7L);
    }

    @Test
    void build_averagesUseEveryCalendarDayWhenPeriodCrossesMonths() {
        LocalDate from = LocalDate.of(2026, 7, 31);
        LocalDate to = LocalDate.of(2026, 9, 1);
        givenSingleSiteCamera();
        when(hourlyRepository.findForReportByChannels(any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        slot("1000001$1$0$0", from, LocalTime.of(9, 0), 33, 0)
                ));

        var report = service.build(from, to, LocalTime.of(9, 0), LocalTime.of(10, 0), "1", "Jour");

        assertThat(report.kpis().dayCount()).isEqualTo(33);
        assertThat(report.dailyBreakdown()).hasSize(33);
        assertThat(report.hourlySlotAverages()).hasSize(1);
        assertThat(report.hourlySlotAverages().get(0).totalEntries()).isEqualTo(33);
        assertThat(report.hourlySlotAverages().get(0).averageEntries()).isEqualTo(1.0);
        assertThat(report.kpis().averageDailyPresence()).isEqualTo(1);
        assertSurfacesConcord(report);
    }

    private static void assertSurfacesConcord(PersonalizedReportResponse report) {
        long kIn = report.kpis().totalEntries();
        long kOut = report.kpis().totalExits();
        assertThat(report.kpis().netPresence()).isEqualTo(kIn - kOut);

        long daysIn = report.dailyBreakdown().stream().mapToLong(r -> r.entries()).sum();
        long daysOut = report.dailyBreakdown().stream().mapToLong(r -> r.exits()).sum();
        assertThat(daysIn).isEqualTo(kIn);
        assertThat(daysOut).isEqualTo(kOut);

        long detailsIn = report.dailyDetails().stream().mapToLong(r -> r.entries()).sum();
        long detailsOut = report.dailyDetails().stream().mapToLong(r -> r.exits()).sum();
        assertThat(detailsIn).isEqualTo(kIn);
        assertThat(detailsOut).isEqualTo(kOut);

        long evoIn = report.dailyEvolution().stream().mapToLong(r -> r.entries()).sum();
        long evoOut = report.dailyEvolution().stream().mapToLong(r -> r.exits()).sum();
        assertThat(evoIn).isEqualTo(kIn);
        assertThat(evoOut).isEqualTo(kOut);

        long weekIn = report.weekdayDistribution().stream().mapToLong(r -> r.entries()).sum();
        long weekOut = report.weekdayDistribution().stream().mapToLong(r -> r.exits()).sum();
        assertThat(weekIn).isEqualTo(kIn);
        assertThat(weekOut).isEqualTo(kOut);

        long hoursIn = report.hourlySlotAverages().stream().mapToLong(r -> r.totalEntries()).sum();
        long hoursOut = report.hourlySlotAverages().stream().mapToLong(r -> r.totalExits()).sum();
        assertThat(hoursIn).isEqualTo(kIn);
        assertThat(hoursOut).isEqualTo(kOut);
        long dayCount = report.kpis().dayCount();
        Map<String, long[]> summedByHourSite = new LinkedHashMap<>();
        for (var day : report.dailyBreakdown()) {
            for (var slot : day.hourlySlots()) {
                var globalHour = report.hourlySlotAverages().stream()
                        .filter(hour -> hour.hourStart().equals(slot.hourStart()))
                        .findFirst()
                        .orElseThrow();
                assertThat(slot.averageEntries()).isEqualTo(globalHour.averageEntries());
                assertThat(slot.averageExits()).isEqualTo(globalHour.averageExits());
                for (var site : slot.sites()) {
                    assertThat(site.presence()).isEqualTo(site.entries() - site.exits());
                    var globalSite = globalHour.sites().stream()
                            .filter(row -> row.siteId().equals(site.siteId()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(site.averageEntries()).isEqualTo(globalSite.averageEntries());
                    assertThat(site.averageExits()).isEqualTo(globalSite.averageExits());
                    long[] acc = summedByHourSite.computeIfAbsent(
                            slot.hourStart() + "|" + site.siteId(), key -> new long[2]);
                    acc[0] += site.entries();
                    acc[1] += site.exits();
                }
            }
        }
        for (var hour : report.hourlySlotAverages()) {
            assertThat(hour.totalPresence()).isEqualTo(hour.totalEntries() - hour.totalExits());
            assertThat(hour.averageEntries()).isEqualTo(expectedAvg(hour.totalEntries(), dayCount));
            assertThat(hour.averageExits()).isEqualTo(expectedAvg(hour.totalExits(), dayCount));
            assertThat(hour.averagePresence()).isEqualTo(expectedAvg(hour.totalPresence(), dayCount));
            long siteIn = hour.sites().stream().mapToLong(s -> s.entries()).sum();
            long siteOut = hour.sites().stream().mapToLong(s -> s.exits()).sum();
            assertThat(siteIn).isEqualTo(hour.totalEntries());
            assertThat(siteOut).isEqualTo(hour.totalExits());
            for (var site : hour.sites()) {
                long[] acc = summedByHourSite.get(hour.hourStart() + "|" + site.siteId());
                assertThat(acc).isNotNull();
                assertThat(acc[0]).isEqualTo(site.entries());
                assertThat(acc[1]).isEqualTo(site.exits());
                assertThat(site.presence()).isEqualTo(site.entries() - site.exits());
                assertThat(site.averageEntries()).isEqualTo(expectedAvg(site.entries(), dayCount));
                assertThat(site.averageExits()).isEqualTo(expectedAvg(site.exits(), dayCount));
            }
        }

        assertThat(report.kpis().dayCount()).isEqualTo(report.dailyBreakdown().size());
        assertThat(report.dailyDetails()).hasSameSizeAs(report.dailyBreakdown());
        assertThat(report.dailyEvolution()).hasSameSizeAs(report.dailyBreakdown());
        if (!report.dailyBreakdown().isEmpty()) {
            assertThat(report.hourlySlotAverages())
                    .hasSameSizeAs(report.dailyBreakdown().get(0).hourlySlots());
        }

        for (int i = 0; i < report.dailyBreakdown().size(); i++) {
            var day = report.dailyBreakdown().get(i);
            long slotIn = day.hourlySlots().stream().mapToLong(s -> s.entries()).sum();
            long slotOut = day.hourlySlots().stream().mapToLong(s -> s.exits()).sum();
            assertThat(slotIn).isEqualTo(day.entries());
            assertThat(slotOut).isEqualTo(day.exits());
            assertThat(day.presenceEndOfSlot()).isEqualTo(day.entries() - day.exits());
            assertThat(report.dailyDetails().get(i).entries()).isEqualTo(day.entries());
            assertThat(report.dailyDetails().get(i).exits()).isEqualTo(day.exits());
            assertThat(report.dailyDetails().get(i).presenceEndOfSlot()).isEqualTo(day.presenceEndOfSlot());
            for (var slot : day.hourlySlots()) {
                assertThat(slot.presence()).isEqualTo(slot.entries() - slot.exits());
                long siteIn = slot.sites().stream().mapToLong(s -> s.entries()).sum();
                long siteOut = slot.sites().stream().mapToLong(s -> s.exits()).sum();
                assertThat(siteIn).isEqualTo(slot.entries());
                assertThat(siteOut).isEqualTo(slot.exits());
            }
        }
    }

    @Test
    void build_listsEachSelectedSitePerSlotAndKeepsTheSum() {
        LocalDate d1 = LocalDate.of(2026, 9, 1);
        LocalDate d2 = LocalDate.of(2026, 9, 2);
        givenSites(OLOUMI, AKANDA);
        when(cameraRepository.findAll()).thenReturn(List.of(
                primary("1000005$1$0$0", "Entree Oloumi", OLOUMI),
                primary("1000004$1$0$0", "Entree Akanda", AKANDA)
        ));
        when(hourlyRepository.findForReportByChannels(
                eq(d1), eq(d2), eq(LocalTime.of(9, 0)), eq(LocalTime.of(9, 0)), any()
        )).thenReturn(List.of(
                slot("1000004$1$0$0", d1, LocalTime.of(9, 0), 10, 3),
                slot("1000005$1$0$0", d2, LocalTime.of(9, 0), 6, 2)
        ));

        var report = service.build(d1, d2, LocalTime.of(9, 0), LocalTime.of(10, 0), "2,1", "Jour");

        assertThat(report.filters().site()).isEqualTo("Akanda, Oloumi");
        var day1Slot = report.dailyBreakdown().get(1).hourlySlots().get(0);
        assertThat(day1Slot.sites()).extracting(s -> s.siteName())
                .containsExactly("Akanda", "Oloumi");
        assertThat(day1Slot.sites()).extracting(s -> s.entries()).containsExactly(10L, 0L);
        assertThat(day1Slot.sites()).extracting(s -> s.exits()).containsExactly(3L, 0L);
        assertThat(day1Slot.entries()).isEqualTo(10);
        assertThat(day1Slot.exits()).isEqualTo(3);
        assertThat(day1Slot.sites().get(0).averageEntries()).isEqualTo(5.0);
        assertThat(day1Slot.sites().get(1).averageEntries()).isEqualTo(3.0);

        var global = report.hourlySlotAverages().get(0);
        assertThat(global.sites()).extracting(s -> s.entries()).containsExactly(10L, 6L);
        assertThat(global.sites()).extracting(s -> s.exits()).containsExactly(3L, 2L);
        assertThat(global.sites()).extracting(s -> s.averageEntries()).containsExactly(5.0, 3.0);
        assertThat(global.totalEntries()).isEqualTo(16);
        assertThat(global.totalExits()).isEqualTo(5);
        assertSurfacesConcord(report);
    }

    @Test
    void build_roundsAveragesHalfUpFromExactCounts() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 3);
        givenSites(AKANDA, OLOUMI);
        when(cameraRepository.findAll()).thenReturn(List.of(
                primary("1000004$1$0$0", "Entree Akanda", AKANDA),
                primary("1000005$1$0$0", "Entree Oloumi", OLOUMI)
        ));
        when(hourlyRepository.findForReportByChannels(
                eq(from), eq(to), eq(LocalTime.of(9, 0)), eq(LocalTime.of(9, 0)), any()
        )).thenReturn(List.of(
                slot("1000004$1$0$0", from, LocalTime.of(9, 0), 1, 0),
                slot("1000005$1$0$0", from.plusDays(1), LocalTime.of(9, 0), 1, 4)
        ));

        var report = service.build(from, to, LocalTime.of(9, 0), LocalTime.of(10, 0), "all", "Jour");

        var hour = report.hourlySlotAverages().get(0);
        assertThat(hour.totalEntries()).isEqualTo(2);
        assertThat(hour.totalExits()).isEqualTo(4);
        assertThat(hour.totalPresence()).isEqualTo(-2);
        assertThat(hour.averageEntries()).isEqualTo(0.7);
        assertThat(hour.averageExits()).isEqualTo(1.3);
        assertThat(hour.averagePresence()).isEqualTo(-0.7);
        assertThat(hour.sites()).extracting(s -> s.entries()).containsExactly(1L, 1L);
        assertThat(hour.sites()).extracting(s -> s.averageEntries()).containsExactly(0.3, 0.3);
        assertThat(hour.sites()).extracting(s -> s.averageExits()).containsExactly(0.0, 1.3);
        assertSurfacesConcord(report);
    }

    @Test
    void build_roundsTieAwayFromZero() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(19);
        givenSingleSiteCamera();
        when(hourlyRepository.findForReportByChannels(any(), any(), any(), any(), any()))
                .thenReturn(List.of(slot("1000001$1$0$0", from, LocalTime.of(8, 0), 1, 0)));

        var report = service.build(from, to, LocalTime.of(8, 0), LocalTime.of(9, 0), "1", "Jour");

        assertThat(report.kpis().dayCount()).isEqualTo(20);
        assertThat(report.hourlySlotAverages().get(0).averageEntries()).isEqualTo(0.1);
        assertThat(report.hourlySlotAverages().get(0).sites().get(0).averageEntries()).isEqualTo(0.1);
        assertSurfacesConcord(report);
    }

    @Test
    void hourSlotsInRange_respectsHalfOpenInterval() {
        assertThat(PersonalizedReportService.hourSlotsInRange(
                LocalTime.of(9, 0), LocalTime.of(11, 0)
        )).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));
    }

    @Test
    void normalizeSiteParam_joinsRepeatedAndCsv() {
        assertThat(ReportController.normalizeSiteParam(List.of("1", "2,3"))).isEqualTo("1,2,3");
        assertThat(ReportController.normalizeSiteParam(List.of("all"))).isEqualTo("all");
        assertThat(ReportController.normalizeSiteParam(List.of("tous"))).isEqualTo("all");
    }

    private void givenSites(SiteEntity... sites) {
        when(siteRepository.findAll()).thenReturn(List.of(sites));
    }

    private void givenSingleSiteCamera() {
        givenSites(AKANDA);
        when(cameraRepository.findAll()).thenReturn(List.of(primary("1000001$1$0$0", "A", AKANDA)));
    }

    @SuppressWarnings("unchecked")
    private java.util.Collection<String> capturedChannelIds() {
        ArgumentCaptor<java.util.Collection<String>> idsCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(hourlyRepository).findForReportByChannels(any(), any(), any(), any(), idsCaptor.capture());
        return idsCaptor.getValue();
    }

    private static double expectedAvg(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static SiteEntity site(Long id, String name) {
        SiteEntity site = new SiteEntity();
        site.setId(id);
        site.setName(name);
        return site;
    }

    private static CameraEntity primary(String channelId, String name, SiteEntity site) {
        return camera(channelId, name, site, true);
    }

    private static CameraEntity camera(String channelId, String name, SiteEntity site, boolean active) {
        CameraEntity cam = new CameraEntity();
        cam.setChannelId(channelId);
        cam.setName(name);
        cam.setSite(site);
        cam.setActive(active);
        return cam;
    }

    private static PeopleCountingHourlyEntity slot(
            String channelId, LocalDate date, LocalTime hour, int entries, int exits
    ) {
        CameraEntity cam = camera(channelId, channelId, null, true);
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
