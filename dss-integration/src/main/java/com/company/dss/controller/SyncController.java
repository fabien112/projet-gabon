package com.company.dss.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.dss.passengerflow.PassengerFlowPollScheduler;
import com.company.dss.service.AuthenticationService;
import com.company.dss.sync.DssDbReconcileService;
import com.company.dss.sync.HistorySyncService;
import com.company.dss.sync.HistorySyncStatus;
import com.company.dss.sync.SyncStatusEventService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final HistorySyncService historySyncService;
    private final PassengerFlowPollScheduler passengerFlowPollScheduler;
    private final AuthenticationService authenticationService;
    private final DssDbReconcileService reconcileService;
    private final SyncStatusEventService syncStatusEventService;

    /**
     * Lance une sync manuelle (historique / rattrapage). Ne démarre jamais toute seule.
     */
    @PostMapping("/history")
    public ResponseEntity<?> startHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        try {
            historySyncService.start(from, to);
            return ResponseEntity.accepted().body(historySyncService.status(passengerFlowPollScheduler.status()));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * Récupère tout ce qui manque en base (jours passés + créneaux du jour en cours).
     */
    @PostMapping("/missing")
    public ResponseEntity<?> startMissing() {
        try {
            historySyncService.startMissingDataSync();
            return ResponseEntity.accepted().body(historySyncService.status(passengerFlowPollScheduler.status()));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        }
    }

    /** Force un relogin DSS (prend la main si le compte est déjà connecté ailleurs). */
    @PostMapping("/dss-reconnect")
    public ResponseEntity<?> reconnectDss() {
        try {
            authenticationService.forceReconnect();
            return ResponseEntity.ok(Map.of(
                    "message", "Session DSS active",
                    "dssSessionActive", true
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "message", "Impossible de reconnecter DSS : " + ex.getMessage(),
                    "dssSessionActive", false
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<HistorySyncStatus> status() {
        return ResponseEntity.ok(historySyncService.status(passengerFlowPollScheduler.status()));
    }

    /** Flux SSE : le frontend reçoit un événement « status » à chaque changement sync/poll. */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter syncEvents() {
        return syncStatusEventService.subscribe();
    }

    /**
     * Compare l'historique DSS (live) avec la base locale, grain horaire, caméras configurées.
     * Lecture seule — n'écrit rien. Max 7 jours.
     */
    @GetMapping("/compare")
    public ResponseEntity<DssDbReconcileService.ReconcileReport> compare(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(reconcileService.compare(from, to));
    }
}
