package com.company.dss.controller;

import java.util.Map;

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

import com.company.dss.camera.CameraService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(cameraService.listConfigured().stream()
                .map(CameraService::toDto)
                .toList());
    }

    @GetMapping("/discover")
    public ResponseEntity<?> discover() {
        return ResponseEntity.ok(cameraService.discoverFromDss());
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody CameraUpsertRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Corps de requête manquant");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CameraService.toDto(cameraService.add(body.channelId(), body.name(), body.site())));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CameraPatchRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Corps de requête manquant");
        }
        return ResponseEntity.ok(CameraService.toDto(
                cameraService.update(id, body.name(), body.site(), body.active())
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> remove(@PathVariable Long id) {
        cameraService.deactivate(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record CameraUpsertRequest(String channelId, String name, String site) {
    }

    public record CameraPatchRequest(String name, String site, Boolean active) {
    }
}
