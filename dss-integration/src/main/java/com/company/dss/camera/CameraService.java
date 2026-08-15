package com.company.dss.camera;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.company.dss.passengerflow.CompteuseCameraRules;
import com.company.dss.passengerflow.CompteuseChannel;
import com.company.dss.passengerflow.PassengerFlowClient;
import com.company.dss.persistence.PeopleCountingSyncService;
import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.repository.CameraRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Catalogue local des caméras (page Config) : source de vérité pour poll, sync et rapports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraRepository cameraRepository;
    private final PassengerFlowClient passengerFlowClient;
    private final PeopleCountingSyncService syncService;

    @Transactional(readOnly = true)
    public List<CameraEntity> listConfigured() {
        return cameraRepository.findAll().stream()
                .sorted(Comparator.comparing(CameraEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CameraEntity> listActive() {
        return cameraRepository.findAll().stream()
                .filter(c -> CompteuseCameraRules.isConfigured(c.getChannelId(), c.isActive()))
                .sorted(Comparator.comparing(CameraEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Canaux à interroger chez DSS : caméras actives en base, ou bootstrap depuis l'arbre DSS.
     */
    @Transactional
    public List<String> channelIdsForSync() {
        List<String> configured = listActive().stream()
                .map(CameraEntity::getChannelId)
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        List<CompteuseChannel> discovered = passengerFlowClient.discoverCompteuseChannels().stream()
                .filter(c -> CompteuseCameraRules.isPrimary(c.channelId(), c.name(), true))
                .toList();
        if (discovered.isEmpty()) {
            return List.of();
        }
        syncService.upsertCameras(discovered);
        return discovered.stream().map(CompteuseChannel::channelId).toList();
    }

    public List<Map<String, Object>> discoverFromDss() {
        List<CompteuseChannel> channels = passengerFlowClient.discoverMainVideoChannels();
        return channels.stream()
                .sorted(Comparator.comparing(CompteuseChannel::name, String.CASE_INSENSITIVE_ORDER))
                .map(ch -> {
                    boolean added = cameraRepository.findByChannelId(ch.channelId())
                            .map(CameraEntity::isActive)
                            .orElse(false);
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("channelId", ch.channelId());
                    dto.put("name", ch.name());
                    dto.put("deviceName", ch.deviceName());
                    dto.put("alreadyAdded", added);
                    return dto;
                })
                .toList();
    }

    @Transactional
    public CameraEntity add(String channelId, String name, String site) {
        String id = requireChannelId(channelId);
        String label = requireName(name);
        CameraEntity camera = cameraRepository.findByChannelId(id).orElseGet(() -> {
            CameraEntity created = new CameraEntity();
            created.setChannelId(id);
            return created;
        });
        camera.setName(label);
        camera.setSite(blankToNull(site));
        camera.setActive(true);
        camera.setManual(true);
        CameraEntity saved = cameraRepository.save(camera);
        log.info(">>> [CAM] Ajoutée {} ({})", saved.getName(), saved.getChannelId());
        return saved;
    }

    @Transactional
    public CameraEntity update(Long id, String name, String site, Boolean active) {
        CameraEntity camera = cameraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Caméra introuvable"));
        if (StringUtils.hasText(name)) {
            camera.setName(name.trim());
            camera.setManual(true);
        }
        if (site != null) {
            camera.setSite(blankToNull(site));
            camera.setManual(true);
        }
        if (active != null) {
            camera.setActive(active);
        }
        return cameraRepository.save(camera);
    }

    @Transactional
    public void deactivate(Long id) {
        CameraEntity camera = cameraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Caméra introuvable"));
        camera.setActive(false);
        cameraRepository.save(camera);
        log.info(">>> [CAM] Désactivée {} ({})", camera.getName(), camera.getChannelId());
    }

    public static Map<String, Object> toDto(CameraEntity camera) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", camera.getId());
        dto.put("channelId", camera.getChannelId());
        dto.put("name", camera.getName());
        dto.put("site", camera.getSite());
        dto.put("active", camera.isActive());
        dto.put("manual", camera.isManual());
        return dto;
    }

    private static String requireChannelId(String channelId) {
        if (!StringUtils.hasText(channelId)) {
            throw new IllegalArgumentException("L'identifiant de canal DSS est obligatoire");
        }
        String id = channelId.trim();
        if (!PassengerFlowClient.isMainVideoChannelCode(id)) {
            throw new IllegalArgumentException(
                    "Identifiant de canal invalide. Format attendu : {device}$1${unité}${canal} "
                            + "(ex. 1000004$1$0$0)."
            );
        }
        return id;
    }

    private static String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Le nom de la caméra est obligatoire");
        }
        return name.trim();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
