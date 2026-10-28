package com.company.dss.site;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.entity.SiteEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.persistence.repository.SiteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Catalogue des sites (page Config) : chaque caméra est rattachée à un site. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteService {

    static final int MAX_NAME_LENGTH = 128;

    private final SiteRepository siteRepository;
    private final CameraRepository cameraRepository;

    @Transactional(readOnly = true)
    public List<SiteEntity> listAll() {
        return siteRepository.findAll().stream()
                .sorted(Comparator.comparing(SiteEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public SiteEntity require(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Le site est obligatoire");
        }
        return siteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Site introuvable"));
    }

    @Transactional
    public SiteEntity rename(Long id, String name) {
        SiteEntity site = require(id);
        String label = requireName(name);
        Optional<SiteEntity> clash = siteRepository.findByNameIgnoreCase(label);
        if (clash.isPresent() && !clash.get().getId().equals(site.getId())) {
            throw new IllegalArgumentException("Le site « " + clash.get().getName() + " » existe déjà");
        }
        site.setName(label);
        return siteRepository.save(site);
    }

    @Transactional
    public void delete(Long id) {
        SiteEntity site = require(id);
        List<CameraEntity> linked = cameraRepository.findBySiteId(site.getId());
        if (!linked.isEmpty()) {
            String names = linked.stream()
                    .map(CameraEntity::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            throw new IllegalArgumentException(
                    "Impossible de supprimer « " + site.getName() + " » : " + linked.size()
                            + " caméra(s) y sont rattachée(s) (" + names + "). Changez d'abord leur site."
            );
        }
        siteRepository.delete(site);
        log.info(">>> [SITE] Supprimé {}", site.getName());
    }

    /** Site existant portant ce nom (casse ignorée), sinon créé. */
    @Transactional
    public SiteEntity findOrCreate(String name) {
        String label = requireName(name);
        return siteRepository.findByNameIgnoreCase(label).orElseGet(() -> {
            SiteEntity site = new SiteEntity();
            site.setName(label);
            SiteEntity saved = siteRepository.save(site);
            log.info(">>> [SITE] Créé automatiquement {}", saved.getName());
            return saved;
        });
    }

    public static Map<String, Object> toDto(SiteEntity site, List<CameraEntity> cameras) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", site.getId());
        dto.put("name", site.getName());
        dto.put("cameraCount", cameras.size());
        dto.put("cameras", cameras.stream()
                .sorted(Comparator.comparing(CameraEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(CameraEntity::getName)
                .toList());
        return dto;
    }

    private static String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Le nom du site est obligatoire");
        }
        String label = name.trim();
        if (label.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Le nom du site ne doit pas dépasser " + MAX_NAME_LENGTH + " caractères");
        }
        return label;
    }
}
