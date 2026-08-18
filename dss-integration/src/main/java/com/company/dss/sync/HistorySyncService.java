package com.company.dss.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.company.dss.authentication.TokenHolder;
import com.company.dss.camera.CameraService;
import com.company.dss.config.DssProperties;
import com.company.dss.exception.DssClientException;
import com.company.dss.passengerflow.PassengerFlowClient;
import com.company.dss.persistence.PeopleCountingSyncService;
import com.company.dss.persistence.entity.SyncMetaEntity;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.persistence.repository.SyncMetaRepository;
import com.company.dss.service.AuthenticationService;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Synchronisation manuelle de l'historique DSS (jamais au démarrage).
 * Upsert jour par jour → relancer la même période complète sans doublons.
 * <p>
 * Anti rate-limit DSS (HTTP 429) : pause entre jours + retry avec backoff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistorySyncService {

    private static final long META_ID = 1L;
    private static final ZoneId GABON = ZoneId.of("Africa/Libreville");
    /** Pause entre deux jours — DSS refuse trop de requêtes rapides (429). */
    private static final long PAUSE_MS_BETWEEN_DAYS = 1_500L;
    private static final int MAX_RETRIES = 5;
    private static final long[] RETRY_BACKOFF_MS = {2_000L, 5_000L, 12_000L, 25_000L, 45_000L};

    private final PassengerFlowClient passengerFlowClient;
    private final PeopleCountingSyncService syncService;
    private final TokenHolder tokenHolder;
    private final AuthenticationService authenticationService;
    private final SyncMetaRepository syncMetaRepository;
    private final PeopleCountingHourlyRepository hourlyRepository;
    private final CameraService cameraService;
    private final DssProperties dssProperties;
    private final ApplicationEventPublisher eventPublisher;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "history-sync");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<LiveProgress> progress = new AtomicReference<>(LiveProgress.idle());

    public boolean isRunning() {
        return running.get();
    }

    public HistorySyncStatus start(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Les dates from et to sont obligatoires");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La date de fin doit être >= date de début");
        }
        try {
            authenticationService.ensureLoggedIn();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Session DSS inactive — impossible de se reconnecter : " + ex.getMessage(),
                    ex
            );
        }
        if (!tokenHolder.hasValidToken()) {
            throw new IllegalStateException("Session DSS inactive. Vérifiez DSS_HOST / identifiants / réseau.");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Une synchronisation est déjà en cours");
        }

        int daysTotal = (int) ChronoUnit.DAYS.between(from, to) + 1;
        Instant startedAt = Instant.now();
        progress.set(new LiveProgress(
                true, "RUNNING", from, to, from, daysTotal, 0, 0,
                "Démarrage…", startedAt, null
        ));
        notifyStatusChanged();
        persistStart(from, to, startedAt);

        executor.submit(() -> runJob(from, to, daysTotal, startedAt));
        return status();
    }

    /**
     * Synchronise automatiquement tout ce qui manque en base (jours passés + créneaux du jour).
     */
    public HistorySyncStatus startMissingDataSync() {
        LocalDate today = LocalDate.now(GABON);
        LocalDate maxInDb = hourlyRepository.findMaxSlotDate().orElse(null);
        SyncMetaEntity meta = syncMetaRepository.findById(META_ID).orElse(null);
        Instant lastPollAt = meta != null ? meta.getLastPollAt() : null;

        CatchUpAdvisor.SyncPlan plan = CatchUpAdvisor.resolveSyncRange(
                today,
                maxInDb,
                lastPollAt,
                Instant.now(),
                dssProperties.getStartupFullSyncDays()
        );
        log.info(">>> [SYNC] Données manquantes — {} ({})", plan.message(), plan.kind());
        return start(plan.from(), plan.to());
    }

    public CatchUpAdvisor.SyncPlan pendingSyncPlan() {
        LocalDate today = LocalDate.now(GABON);
        LocalDate maxInDb = hourlyRepository.findMaxSlotDate().orElse(null);
        SyncMetaEntity meta = syncMetaRepository.findById(META_ID).orElse(null);
        Instant lastPollAt = moreRecent(
                meta != null ? meta.getLastPollAt() : null,
                null
        );
        return CatchUpAdvisor.resolveSyncRange(
                today,
                maxInDb,
                lastPollAt,
                Instant.now(),
                dssProperties.getStartupFullSyncDays()
        );
    }

    public HistorySyncStatus status() {
        return status(null);
    }

    public HistorySyncStatus status(com.company.dss.passengerflow.PassengerFlowPollStatus poll) {
        LiveProgress live = progress.get();
        SyncMetaEntity meta = syncMetaRepository.findById(META_ID).orElse(null);
        LocalDate maxInDb = hourlyRepository.findMaxSlotDate().orElse(null);

        Instant lastPollAt = moreRecent(
                meta != null ? meta.getLastPollAt() : null,
                poll != null ? poll.lastAt() : null
        );
        CatchUpAdvisor.SyncPlan pending = CatchUpAdvisor.resolveSyncRange(
                LocalDate.now(GABON),
                maxInDb,
                lastPollAt,
                Instant.now(),
                dssProperties.getStartupFullSyncDays()
        );

        return new HistorySyncStatus(
                live.running(),
                live.phase(),
                live.fromDate(),
                live.toDate(),
                live.currentDate(),
                live.daysTotal(),
                live.daysDone(),
                live.rowsUpserted(),
                live.message(),
                live.startedAt(),
                live.finishedAt(),
                meta != null && meta.getLastFinishedAt() != null
                        ? meta.getLastFinishedAt().atZone(GABON).toLocalDate()
                        : null,
                maxInDb,
                meta != null ? meta.getLastFinishedAt() : null,
                meta != null ? meta.getLastStatus() : null,
                meta != null ? meta.getLastMessage() : null,
                meta != null ? meta.getLastFromDate() : null,
                meta != null ? meta.getLastToDate() : null,
                tokenHolder.hasValidToken(),
                poll != null && poll.enabled(),
                poll != null && poll.inProgress(),
                poll != null && poll.suspended(),
                poll != null ? poll.interval() : null,
                lastPollAt,
                poll != null ? poll.lastStatus() : null,
                poll != null ? poll.lastMessage() : null,
                poll != null ? poll.lastRowsUpserted() : 0,
                poll != null ? poll.lastActiveSlots() : 0,
                poll != null ? poll.lastDate() : (meta != null ? meta.getLastPollDate() : null),
                !"TODAY".equals(pending.kind()) || isStalePoll(lastPollAt),
                pending.kind(),
                pending.from(),
                pending.to(),
                pending.message()
        );
    }

    private static boolean isStalePoll(Instant lastPollAt) {
        if (lastPollAt == null) {
            return false;
        }
        return java.time.Duration.between(lastPollAt, Instant.now()).compareTo(CatchUpAdvisor.STALE_AFTER) > 0;
    }

    public void rememberPollSuccess(Instant at, LocalDate date) {
        SyncMetaEntity meta = syncMetaRepository.findById(META_ID).orElseGet(SyncMetaEntity::new);
        meta.setId(META_ID);
        meta.setLastPollAt(at);
        meta.setLastPollDate(date);
        syncMetaRepository.save(meta);
    }

    private static Instant moreRecent(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private void runJob(LocalDate from, LocalDate to, int daysTotal, Instant startedAt) {
        int daysDone = 0;
        int rowsTotal = 0;
        LocalDate current = from;
        try {
            authenticationService.ensureLoggedIn();
            List<String> channelIds = cameraService.channelIdsForSync();
            if (channelIds.isEmpty()) {
                throw new IllegalStateException(
                        "Aucune caméra configurée. Ajoutez-en une dans Config, ou vérifiez la session DSS."
                );
            }
            log.info(">>> [SYNC] Début sync manuelle {} → {} ({} jours, {} canaux)",
                    from, to, daysTotal, channelIds.size());

            while (!current.isAfter(to)) {
                progress.set(new LiveProgress(
                        true, "RUNNING", from, to, current, daysTotal, daysDone, rowsTotal,
                        "Sync du " + current + " (" + (daysDone + 1) + "/" + daysTotal + ")",
                        startedAt, null
                ));
                notifyStatusChanged();

                List<Map<String, Object>> rows = fetchDayWithRetry(
                        current, channelIds, from, to, daysTotal, daysDone, rowsTotal, startedAt
                );
                int saved = syncService.upsertRows(rows);
                rowsTotal += saved;
                daysDone++;

                log.info(">>> [SYNC] {} — upsert={} — caméras={} (total lignes={})",
                        current, saved, channelIds.size(), rowsTotal);

                // Keep-alive session + pause anti-429
                if (!current.equals(to)) {
                    if (daysDone % 10 == 0) {
                        try {
                            authenticationService.ensureLoggedIn();
                        } catch (Exception ex) {
                            log.warn(">>> [SYNC] Relogin périodique échoué : {}", ex.getMessage());
                        }
                    }
                    Thread.sleep(PAUSE_MS_BETWEEN_DAYS);
                }
                current = current.plusDays(1);
            }

            Instant finishedAt = Instant.now();
            String msg = "Terminé — " + daysDone + " jour(s), " + rowsTotal + " ligne(s) upsert";
            progress.set(new LiveProgress(
                    false, "SUCCESS", from, to, to, daysTotal, daysDone, rowsTotal,
                    msg, startedAt, finishedAt
            ));
            notifyStatusChanged();
            persistFinish("SUCCESS", from, to, finishedAt, daysDone, rowsTotal, msg);
            log.info(">>> [SYNC] {}", msg);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            fail(from, to, daysTotal, daysDone, rowsTotal, startedAt, "Interrompu");
        } catch (Exception ex) {
            fail(from, to, daysTotal, daysDone, rowsTotal, startedAt, friendlyError(ex));
            log.warn(">>> [SYNC] Échec : {}", ex.getMessage());
        } finally {
            running.set(false);
            notifyStatusChanged();
        }
    }

    /**
     * Récupère un jour avec retry sur 429 / 503 / 401.
     */
    private List<Map<String, Object>> fetchDayWithRetry(
            LocalDate day,
            List<String> channelIds,
            LocalDate from,
            LocalDate to,
            int daysTotal,
            int daysDone,
            int rowsTotal,
            Instant startedAt
    ) throws InterruptedException {
        Exception last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return passengerFlowClient.fetchHistoryForDate(day, channelIds);
            } catch (DssClientException ex) {
                last = ex;
                int code = ex.getStatusCode();
                if (code == 401) {
                    log.warn(">>> [SYNC] 401 sur {} — relogin puis retry", day);
                    progress.set(new LiveProgress(
                            true, "RUNNING", from, to, day, daysTotal, daysDone, rowsTotal,
                            "Session expirée — reconnexion DSS…", startedAt, null
                    ));
                    notifyStatusChanged();
                    authenticationService.ensureLoggedIn();
                    Thread.sleep(1_000L);
                    continue;
                }
                if (code == 429 || code == 503) {
                    long wait = RETRY_BACKOFF_MS[Math.min(attempt, RETRY_BACKOFF_MS.length - 1)];
                    log.warn(">>> [SYNC] HTTP {} sur {} — pause {}s puis retry ({}/{})",
                            code, day, wait / 1000, attempt + 1, MAX_RETRIES);
                    progress.set(new LiveProgress(
                            true, "RUNNING", from, to, day, daysTotal, daysDone, rowsTotal,
                            "DSS saturé (HTTP " + code + ") — pause " + (wait / 1000) + "s…",
                            startedAt, null
                    ));
                    notifyStatusChanged();
                    Thread.sleep(wait);
                    continue;
                }
                throw ex;
            }
        }
        throw new IllegalStateException(
                "DSS refuse toujours les requêtes après " + MAX_RETRIES + " tentatives sur " + day
                        + (last != null ? " : " + last.getMessage() : ""),
                last
        );
    }

    private static String friendlyError(Exception ex) {
        if (ex instanceof DssClientException dss && dss.getStatusCode() == 429) {
            return "DSS a limité le débit (HTTP 429). Réessayez dans 1–2 minutes "
                    + "ou synchronisez par tranches plus courtes.";
        }
        String msg = ex.getMessage();
        if (msg != null && msg.contains("429")) {
            return "DSS a limité le débit (HTTP 429). Réessayez dans 1–2 minutes "
                    + "ou synchronisez par tranches plus courtes.";
        }
        return msg != null ? msg : ex.getClass().getSimpleName();
    }

    private void fail(
            LocalDate from, LocalDate to, int daysTotal, int daysDone, int rowsTotal,
            Instant startedAt, String error
    ) {
        Instant finishedAt = Instant.now();
        String msg = "Échec après " + daysDone + "/" + daysTotal + " jour(s) : " + error;
        progress.set(new LiveProgress(
                false, "FAILED", from, to,
                progress.get().currentDate(), daysTotal, daysDone, rowsTotal,
                msg, startedAt, finishedAt
        ));
        notifyStatusChanged();
        persistFinish("FAILED", from, to, finishedAt, daysDone, rowsTotal, msg);
    }

    private void notifyStatusChanged() {
        eventPublisher.publishEvent(new SyncStatusChangedEvent());
    }

    private void persistStart(LocalDate from, LocalDate to, Instant startedAt) {
        SyncMetaEntity meta = syncMetaRepository.findById(META_ID).orElseGet(SyncMetaEntity::new);
        meta.setId(META_ID);
        meta.setLastStartedAt(startedAt);
        meta.setLastFromDate(from);
        meta.setLastToDate(to);
        meta.setLastStatus("RUNNING");
        meta.setLastMessage("En cours…");
        meta.setLastDaysProcessed(0);
        meta.setLastRowsUpserted(0);
        syncMetaRepository.save(meta);
    }

    private void persistFinish(
            String status, LocalDate from, LocalDate to, Instant finishedAt,
            int daysDone, int rows, String message
    ) {
        SyncMetaEntity meta = syncMetaRepository.findById(META_ID).orElseGet(SyncMetaEntity::new);
        meta.setId(META_ID);
        meta.setLastFinishedAt(finishedAt);
        meta.setLastFromDate(from);
        meta.setLastToDate(to);
        meta.setLastStatus(status);
        meta.setLastMessage(message);
        meta.setLastDaysProcessed(daysDone);
        meta.setLastRowsUpserted(rows);
        if ("SUCCESS".equals(status)) {
            meta.setLastPollAt(finishedAt);
            meta.setLastPollDate(to);
        }
        syncMetaRepository.save(meta);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record LiveProgress(
            boolean running,
            String phase,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate currentDate,
            int daysTotal,
            int daysDone,
            int rowsUpserted,
            String message,
            Instant startedAt,
            Instant finishedAt
    ) {
        static LiveProgress idle() {
            return new LiveProgress(false, "IDLE", null, null, null, 0, 0, 0, "Aucune sync en cours", null, null);
        }
    }
}
