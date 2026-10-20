package com.company.dss.camera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.dss.passengerflow.CompteuseChannel;
import com.company.dss.passengerflow.PassengerFlowClient;
import com.company.dss.persistence.PeopleCountingSyncService;
import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.entity.SiteEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.site.SiteService;

@ExtendWith(MockitoExtension.class)
class CameraServiceTest {

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private PassengerFlowClient passengerFlowClient;

    @Mock
    private PeopleCountingSyncService syncService;

    @Mock
    private SiteService siteService;

    private CameraService service;

    @BeforeEach
    void setUp() {
        service = new CameraService(cameraRepository, passengerFlowClient, syncService, siteService);
    }

    @Test
    void add_persistsManualActiveCameraLinkedToSite() {
        SiteEntity akanda = site(1L, "Akanda");
        when(siteService.require(1L)).thenReturn(akanda);
        when(cameraRepository.findByChannelId("1000004$1$0$0")).thenReturn(Optional.empty());
        when(cameraRepository.save(any(CameraEntity.class))).thenAnswer(inv -> {
            CameraEntity cam = inv.getArgument(0);
            cam.setId(12L);
            return cam;
        });

        CameraEntity saved = service.add("1000004$1$0$0", "Entree Parking", 1L);

        ArgumentCaptor<CameraEntity> captor = ArgumentCaptor.forClass(CameraEntity.class);
        verify(cameraRepository).save(captor.capture());
        CameraEntity persisted = captor.getValue();
        assertThat(persisted.getChannelId()).isEqualTo("1000004$1$0$0");
        assertThat(persisted.getName()).isEqualTo("Entree Parking");
        assertThat(persisted.getSite()).isSameAs(akanda);
        assertThat(persisted.isActive()).isTrue();
        assertThat(persisted.isManual()).isTrue();
        assertThat(saved.getId()).isEqualTo(12L);
        assertThat(CameraService.toDto(saved))
                .containsEntry("siteId", 1L)
                .containsEntry("site", "Akanda");
    }

    @Test
    void add_requiresSite() {
        when(siteService.require(null)).thenThrow(new IllegalArgumentException("Le site est obligatoire"));

        assertThatThrownBy(() -> service.add("1000004$1$0$0", "Entree", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site est obligatoire");
        verify(cameraRepository, never()).save(any());
    }

    @Test
    void add_rejectsTechnicalChannel() {
        assertThatThrownBy(() -> service.add("1000004$3$1$0", "Doublon", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Identifiant de canal invalide");
        verify(cameraRepository, never()).save(any());
    }

    @Test
    void update_changesSite() {
        CameraEntity cam = new CameraEntity();
        cam.setId(3L);
        cam.setChannelId("1000005$1$0$1");
        cam.setName("Sortie");
        cam.setSite(site(1L, "Akanda"));
        SiteEntity oloumi = site(2L, "Oloumi");
        when(cameraRepository.findById(3L)).thenReturn(Optional.of(cam));
        when(siteService.require(2L)).thenReturn(oloumi);
        when(cameraRepository.save(any(CameraEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CameraEntity updated = service.update(3L, null, 2L, null);

        assertThat(updated.getSite()).isSameAs(oloumi);
        assertThat(updated.isManual()).isTrue();
    }

    @Test
    void channelIdsForSync_usesConfiguredCamerasWithoutRediscovery() {
        CameraEntity cam = new CameraEntity();
        cam.setChannelId("1000006$1$0$0");
        cam.setName("Parking");
        cam.setActive(true);
        when(cameraRepository.findAll()).thenReturn(List.of(cam));

        List<String> ids = service.channelIdsForSync();

        assertThat(ids).containsExactly("1000006$1$0$0");
        verify(passengerFlowClient, never()).discoverCompteuseChannels();
        verify(syncService, never()).upsertCameras(any());
    }

    @Test
    void channelIdsForSync_bootstrapsFromDssWhenEmpty() {
        when(cameraRepository.findAll()).thenReturn(List.of());
        when(passengerFlowClient.discoverCompteuseChannels()).thenReturn(List.of(
                new CompteuseChannel("1000004$1$0$0", "Cam_Compteuse_Entree_Akanda", "NVR")
        ));

        List<String> ids = service.channelIdsForSync();

        assertThat(ids).containsExactly("1000004$1$0$0");
        verify(syncService).upsertCameras(any());
    }

    private static SiteEntity site(Long id, String name) {
        SiteEntity site = new SiteEntity();
        site.setId(id);
        site.setName(name);
        return site;
    }
}
