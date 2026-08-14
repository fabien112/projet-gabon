package com.company.dss.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.camera.CameraConfigService;
import com.company.dss.dto.camera.AddCameraRequest;
import com.company.dss.dto.camera.CameraDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraConfigService cameraConfigService;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(cameraConfigService.listConfigured());
    }

    @GetMapping("/available")
    public ResponseEntity<?> available() {
        return ResponseEntity.ok(cameraConfigService.listAvailableFromDss());
    }

    @PostMapping
    public ResponseEntity<?> add(@Valid @RequestBody AddCameraRequest request) {
        CameraDto created = cameraConfigService.add(request.channelId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> remove(@PathVariable Long id) {
        cameraConfigService.remove(id);
        return ResponseEntity.ok(Map.of("ok", true, "id", id));
    }
}
