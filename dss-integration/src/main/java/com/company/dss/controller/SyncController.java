package com.company.dss.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.sync.HistorySyncService;
import com.company.dss.sync.HistorySyncStatus;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final HistorySyncService historySyncService;

    /**
     * Lance une sync manuelle (historique / rattrapage). Ne démarre jamais toute seule.
     */
    @PostMapping("/history")
    public ResponseEntity<?> startHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        try {
            HistorySyncStatus status = historySyncService.start(from, to);
            return ResponseEntity.accepted().body(status);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<HistorySyncStatus> status() {
        return ResponseEntity.ok(historySyncService.status());
    }
}
