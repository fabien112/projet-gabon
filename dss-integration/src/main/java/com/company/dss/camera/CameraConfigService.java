package com.company.dss.camera;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.company.dss.dto.camera.AvailableCameraDto;
import com.company.dss.dto.camera.CameraDto;
import com.company.dss.passengerflow.CompteuseCameraRules;
import com.company.dss.passengerflow.CompteuseChannel;
import com.company.dss.passengerflow.PassengerFlowClient;
import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.service.AuthenticationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caméras de comptage configurées : source de vérité pour poll, sync et rapports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CameraConfigService {

    private final CameraRepository cameraRepository;
    private final PassengerFlowClient passengerFlowClient;
    private final AuthenticationService authenticationService;

    public List<CameraDto> listConfigured() {
        return configuredEntities().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Canaux actifs à interroger (poll + sync historique).
     */
    public List<CompteuseChannel> listConfiguredChannels() {
        return configuredEntities().stream()
                .map(c -> new CompteuseChannel(
                        c.getChannelId(),
                        c.getName(),
                        c.getSite() != null ? c.getSite() : ""
                ))
                .toList();
    }

    public List<AvailableCameraDto> listAvailableFromDss() {
        authenticationService.ensureLoggedIn();
        Set<String> configuredIds = cameraRepository.findByActiveTrue().stream()
                .map(CameraEntity::getChannelId)
                .collect(Collectors.toSet());
        return passengerFlowClient.discoverVideoChannels().stream()
                .filter(c -> CompteuseCameraRules.isPrimary(c.channelId(), c.name(), true))
                .sorted(Comparator.comparing(CompteuseChannel::name, String.CASE_INSENSITIVE_ORDER))
                .map(c -> new AvailableCameraDto(
                        c.channelId(),
                        c.name(),
                        c.deviceName(),
                        configuredIds.contains(c.channelId())
                ))
                .toList();
    }

    public CameraDto add(String channelId) {
        if (!StringUtils.hasText(channelId)) {
            throw new IllegalArgumentException("channelId est obligatoire");
        }
        String id = channelId.trim();
        if (!PassengerFlowClient.isMainVideoChannelCode(id)) {
            throw new IllegalArgumentException(
                    "Seuls les canaux vidéo principaux ($1$) peuvent être ajoutés au comptage."
            );
        }

        authenticationService.ensureLoggedIn();
        CompteuseChannel found = passengerFlowClient.discoverVideoChannels().stream()
                .filter(c -> id.equals(c.channelId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Caméra introuvable dans DSS : " + id
                ));

        CameraEntity camera = cameraRepository.findByChannelId(id).orElseGet(() -> {
            CameraEntity created = new CameraEntity();
            created.setChannelId(id);
            return created;
        });
        if (camera.getId() != null && camera.isActive()) {
            throw new IllegalArgumentException("Cette caméra est déjà ajoutée au comptage.");
        }

        String name = StringUtils.hasText(found.name()) ? found.name() : id;
        camera.setName(name);
        camera.setSite(guessSite(name, found.deviceName()));
        camera.setActive(true);
        camera = cameraRepository.save(camera);
        log.info(">>> [CAM] Caméra ajoutée au comptage : {} ({})", name, id);
        return toDto(camera);
    }

    @Transactional
    public void remove(Long id) {
        CameraEntity camera = cameraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Caméra introuvable : " + id));
        camera.setActive(false);
        cameraRepository.save(camera);
        log.info(">>> [CAM] Caméra retirée du comptage : {} ({})", camera.getName(), camera.getChannelId());
    }

    private List<CameraEntity> configuredEntities() {
        return cameraRepository.findByActiveTrue().stream()
                .filter(c -> CompteuseCameraRules.isPrimary(c.getChannelId(), c.getName(), true))
                .sorted(Comparator.comparing(CameraEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private CameraDto toDto(CameraEntity camera) {
        return new CameraDto(
                camera.getId(),
                camera.getChannelId(),
                camera.getName(),
                camera.getSite(),
                camera.isActive()
        );
    }

    private static String guessSite(String cameraName, String deviceName) {
        String fromName = siteFrom(cameraName);
        if (fromName != null) {
            return fromName;
        }
        return siteFrom(deviceName);
    }

    private static String siteFrom(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String upper = value.toUpperCase();
        if (upper.contains("AKANDA")) {
            return "Akanda";
        }
        if (upper.contains("OLOUMI")) {
            return "Oloumi";
        }
        return null;
    }
}
