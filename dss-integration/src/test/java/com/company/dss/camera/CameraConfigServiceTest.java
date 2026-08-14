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

import com.company.dss.dto.camera.CameraDto;
import com.company.dss.passengerflow.CompteuseChannel;
import com.company.dss.passengerflow.PassengerFlowClient;
import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.service.AuthenticationService;

@ExtendWith(MockitoExtension.class)
class CameraConfigServiceTest {

    @Mock
    private CameraRepository cameraRepository;
    @Mock
    private PassengerFlowClient passengerFlowClient;
    @Mock
    private AuthenticationService authenticationService;

    private CameraConfigService service;

    @BeforeEach
    void setUp() {
        service = new CameraConfigService(
                cameraRepository, passengerFlowClient, authenticationService
        );
    }

    @Test
    void listConfigured_skipsInactiveAndTechnicalDuplicates() {
        when(cameraRepository.findByActiveTrue()).thenReturn(List.of(
                camera(1L, "1000004$1$0$0", "Hall", true),
                camera(2L, "1000004$3$1$0", "Hall_1", true)
        ));

        List<CameraDto> listed = service.listConfigured();

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).channelId()).isEqualTo("1000004$1$0$0");
    }

    @Test
    void add_persistsConfiguredCamera() {
        CompteuseChannel dssCam = new CompteuseChannel(
                "1000009$1$0$0", "Hall principal", "NVR Akanda"
        );
        when(passengerFlowClient.discoverVideoChannels()).thenReturn(List.of(dssCam));
        when(cameraRepository.findByChannelId("1000009$1$0$0")).thenReturn(Optional.empty());
        when(cameraRepository.save(any(CameraEntity.class))).thenAnswer(inv -> {
            CameraEntity saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        CameraDto dto = service.add("1000009$1$0$0");

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.name()).isEqualTo("Hall principal");
        assertThat(dto.active()).isTrue();
        verify(authenticationService).ensureLoggedIn();

        ArgumentCaptor<CameraEntity> captor = ArgumentCaptor.forClass(CameraEntity.class);
        verify(cameraRepository).save(captor.capture());
        assertThat(captor.getValue().getChannelId()).isEqualTo("1000009$1$0$0");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void add_rejectsNonPrimaryChannel() {
        assertThatThrownBy(() -> service.add("1000004$3$1$0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$1$");
        verify(cameraRepository, never()).save(any());
    }

    @Test
    void add_rejectsAlreadyActive() {
        CameraEntity existing = camera(7L, "1000009$1$0$0", "Hall", true);
        CompteuseChannel dssCam = new CompteuseChannel(
                "1000009$1$0$0", "Hall principal", "NVR"
        );
        when(passengerFlowClient.discoverVideoChannels()).thenReturn(List.of(dssCam));
        when(cameraRepository.findByChannelId("1000009$1$0$0")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.add("1000009$1$0$0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà ajoutée");
    }

    private static CameraEntity camera(Long id, String channelId, String name, boolean active) {
        CameraEntity cam = new CameraEntity();
        cam.setId(id);
        cam.setChannelId(channelId);
        cam.setName(name);
        cam.setActive(active);
        return cam;
    }
}
