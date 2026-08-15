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
import com.company.dss.persistence.repository.CameraRepository;

@ExtendWith(MockitoExtension.class)
class CameraServiceTest {

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private PassengerFlowClient passengerFlowClient;

    @Mock
    private PeopleCountingSyncService syncService;

    private CameraService service;

    @BeforeEach
    void setUp() {
        service = new CameraService(cameraRepository, passengerFlowClient, syncService);
    }

    @Test
    void add_persistsManualActiveCamera() {
        when(cameraRepository.findByChannelId("1000004$1$0$0")).thenReturn(Optional.empty());
        when(cameraRepository.save(any(CameraEntity.class))).thenAnswer(inv -> {
            CameraEntity cam = inv.getArgument(0);
            cam.setId(12L);
            return cam;
        });

        CameraEntity saved = service.add("1000004$1$0$0", "Entree Parking", "Akanda");

        ArgumentCaptor<CameraEntity> captor = ArgumentCaptor.forClass(CameraEntity.class);
        verify(cameraRepository).save(captor.capture());
        CameraEntity persisted = captor.getValue();
        assertThat(persisted.getChannelId()).isEqualTo("1000004$1$0$0");
        assertThat(persisted.getName()).isEqualTo("Entree Parking");
        assertThat(persisted.getSite()).isEqualTo("Akanda");
        assertThat(persisted.isActive()).isTrue();
        assertThat(persisted.isManual()).isTrue();
        assertThat(saved.getId()).isEqualTo(12L);
    }

    @Test
    void add_rejectsTechnicalChannel() {
        assertThatThrownBy(() -> service.add("1000004$3$1$0", "Doublon", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Identifiant de canal invalide");
        verify(cameraRepository, never()).save(any());
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
}
