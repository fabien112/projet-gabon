package com.company.dss.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.company.dss.authentication.TokenHolder;
import com.company.dss.passengerflow.PassengerFlowClient;
import com.company.dss.persistence.PeopleCountingSyncService;
import com.company.dss.persistence.entity.SyncMetaEntity;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.persistence.repository.SyncMetaRepository;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Synchronisation manuelle de l'historique DSS (jamais au démarrage).
 * Upsert jour par jour → relancer la même période complète sans doublons.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistorySyncService {

    private static final long META_ID = 1L;
    private static final long PAUSE_MS_BETWEEN_DAYS = 250L;

    private final PassengerFlowClient passengerFlowClient;
    private final PeopleCountingSyncService syncService;
    private final TokenHolder tokenHolder;
    private final SyncMetaRepository syncMetaRepository;
    private final PeopleCountingHourlyRepository hourlyRepository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "history-sync");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<LiveProgress> progress = new AtomicReference<>(LiveProgress.idle());

    public HistorySyncStatus start(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Les dates from et to sont obligatoires");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La date de fin doit être >= date de début");
        }
        if (!tokenHolder.hasValidToken()) {
            throw new IllegalStateException("Session DSS inactive. Vérifiez la connexion / auto-login.");
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
        persistStart(from, to, startedAt);

        executor.submit(() -> runJob(from, to, daysTotal, startedAt));
        return status();
    }

    public HistorySyncStatus status() {
        LiveProgress live = progress.get();
        SyncMetaEntity meta = syncMetaRepository.findById(META_ID).orElse(null);
        LocalDate maxInDb = hourlyRepository.findMaxSlotDate().orElse(null);

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
                        ? meta.getLastFinishedAt().atZone(java.time.ZoneId.of("Africa/Libreville")).toLocalDate()
                        : null,
                maxInDb,
                meta != null ? meta.getLastFinishedAt() : null,
                meta != null ? meta.getLastStatus() : null,
                meta != null ? meta.getLastMessage() : null,
                meta != null ? meta.getLastFromDate() : null,
                meta != null ? meta.getLastToDate() : null
        );
    }

    private void runJob(LocalDate from, LocalDate to, int daysTotal, Instant startedAt) {
        int daysDone = 0;
        int rowsTotal = 0;
        LocalDate current = from;
        try {
            List<String> channelIds = passengerFlowClient.discoverCompteuseChannelIds();
            if (channelIds.isEmpty()) {
                throw new IllegalStateException("Aucun canal Compteuse trouvé dans DSS");
            }
            log.info(">>> [SYNC] Début sync manuelle {} → {} ({} jours, {} canaux)",
                    from, to, daysTotal, channelIds.size());

            while (!current.isAfter(to)) {
                progress.set(new LiveProgress(
                        true, "RUNNING", from, to, current, daysTotal, daysDone, rowsTotal,
                        "Sync du " + current, startedAt, null
                ));

                List<Map<String, Object>> rows = passengerFlowClient.fetchHistoryForDate(current, channelIds);
                int saved = syncService.upsertRows(rows);
                rowsTotal += saved;
                daysDone++;

                log.info(">>> [SYNC] {} — upsert={} (total lignes={})", current, saved, rowsTotal);

                if (!current.equals(to)) {
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
            persistFinish("SUCCESS", from, to, finishedAt, daysDone, rowsTotal, msg);
            log.info(">>> [SYNC] {}", msg);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            fail(from, to, daysTotal, daysDone, rowsTotal, startedAt, "Interrompu");
        } catch (Exception ex) {
            fail(from, to, daysTotal, daysDone, rowsTotal, startedAt, ex.getMessage());
            log.warn(">>> [SYNC] Échec : {}", ex.getMessage());
        } finally {
            running.set(false);
        }
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
        persistFinish("FAILED", from, to, finishedAt, daysDone, rowsTotal, msg);
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
