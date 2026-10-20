package com.company.dss.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.persistence.entity.CameraEntity;
import com.company.dss.persistence.repository.CameraRepository;
import com.company.dss.site.SiteService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;
    private final CameraRepository cameraRepository;

    @GetMapping
    public ResponseEntity<?> list() {
        Map<Long, List<CameraEntity>> bySite = cameraRepository.findAll().stream()
                .filter(c -> c.getSite() != null)
                .collect(Collectors.groupingBy(c -> c.getSite().getId()));
        return ResponseEntity.ok(siteService.listAll().stream()
                .map(site -> SiteService.toDto(site, bySite.getOrDefault(site.getId(), List.of())))
                .toList());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SiteRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Corps de requête manquant");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SiteService.toDto(siteService.create(body.name()), List.of()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> rename(@PathVariable Long id, @RequestBody SiteRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Corps de requête manquant");
        }
        var site = siteService.rename(id, body.name());
        return ResponseEntity.ok(SiteService.toDto(site, cameraRepository.findBySiteId(site.getId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        siteService.delete(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record SiteRequest(String name) {
    }
}
