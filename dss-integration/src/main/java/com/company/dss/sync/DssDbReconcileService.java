package com.company.dss.sync;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.company.dss.authentication.TokenHolder;
import com.company.dss.camera.CameraService;
import com.company.dss.passengerflow.PassengerFlowClient;
import com.company.dss.persistence.entity.PeopleCountingHourlyEntity;
import com.company.dss.persistence.repository.PeopleCountingHourlyRepository;
import com.company.dss.service.AuthenticationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Compare l'historique People Counting DSS (live) avec la base locale, même grain :
 * caméra × jour × heure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DssDbReconcileService {

    private static final DateTimeFormatter DSS_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DAYS = 7;
    private static final int MAX_DIFFS = 40;
    private static final long PAUSE_MS_BETWEEN_DAYS = 1_200L;

    private final AuthenticationService authenticationService;
    private final TokenHolder tokenHolder;
    private final CameraService cameraService;
    private final PassengerFlowClient passengerFlowClient;
    private final PeopleCountingHourlyRepository hourlyRepository;
    private final HistorySyncService historySyncService;

    public ReconcileReport compare(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Les dates from et to sont obligatoires");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La date de fin doit être >= date de début");
        }
        int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new IllegalArgumentException(
                    "Comparaison limitée à " + MAX_DAYS + " jours (reçu : " + days + ")."
            );
        }
        if (historySyncService.isRunning()) {
            throw new IllegalStateException("Une synchronisation est en cours — attendez la fin avant de comparer.");
        }
        authenticationService.ensureLoggedIn();
        if (!tokenHolder.hasValidToken()) {
            throw new IllegalStateException("Session DSS inactive. Reconnectez-vous depuis Config.");
        }

        List<String> channelIds = cameraService.channelIdsForSync();
        if (channelIds.isEmpty()) {
            throw new IllegalStateException("Aucune caméra configurée à comparer.");
        }

        Map<SlotKey, Counts> dss = new LinkedHashMap<>();
        LocalDate day = from;
        while (!day.isAfter(to)) {
            List<Map<String, Object>> rows = passengerFlowClient.fetchHistoryForDate(day, channelIds);
            for (Map<String, Object> row : rows) {
                SlotKey key = keyFromDss(row);
                if (key == null) {
                    continue;
                }
                Counts counts = countsFromDss(row);
                dss.merge(key, counts, Counts::plus);
            }
            if (!day.equals(to)) {
                try {
                    Thread.sleep(PAUSE_MS_BETWEEN_DAYS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Comparaison interrompue");
                }
            }
            day = day.plusDays(1);
        }

        Map<SlotKey, Counts> db = new LinkedHashMap<>();
        List<PeopleCountingHourlyEntity> local = hourlyRepository.findForReconcile(from, to, channelIds);
        for (PeopleCountingHourlyEntity row : local) {
            SlotKey key = new SlotKey(row.getCamera().getChannelId(), row.getSlotDate(), row.getHourStart());
            db.merge(key, new Counts(row.getEntries(), row.getExits()), Counts::plus);
        }

        return buildReport(from, to, channelIds.size(), dss, db);
    }

    static ReconcileReport buildReport(
            LocalDate from,
            LocalDate to,
            int cameras,
            Map<SlotKey, Counts> dss,
            Map<SlotKey, Counts> db
    ) {
        int matches = 0;
        int missingInDb = 0;
        int extraInDb = 0;
        int valueMismatches = 0;
        long dssEntries = 0;
        long dssExits = 0;
        long dbEntries = 0;
        long dbExits = 0;
        List<DiffSample> diffs = new ArrayList<>();

        for (Counts c : dss.values()) {
            dssEntries += c.entries();
            dssExits += c.exits();
        }
        for (Counts c : db.values()) {
            dbEntries += c.entries();
            dbExits += c.exits();
        }

        for (Map.Entry<SlotKey, Counts> e : dss.entrySet()) {
            Counts local = db.get(e.getKey());
            if (local == null) {
                missingInDb++;
                addDiff(diffs, "MISSING_IN_DB", e.getKey(), e.getValue(), null);
            } else if (local.entries() == e.getValue().entries() && local.exits() == e.getValue().exits()) {
                matches++;
            } else {
                valueMismatches++;
                addDiff(diffs, "VALUE", e.getKey(), e.getValue(), local);
            }
        }
        for (Map.Entry<SlotKey, Counts> e : db.entrySet()) {
            if (!dss.containsKey(e.getKey()) && e.getValue().total() > 0) {
                extraInDb++;
                addDiff(diffs, "EXTRA_IN_DB", e.getKey(), null, e.getValue());
            }
        }

        boolean aligned = missingInDb == 0 && valueMismatches == 0;
        String message;
        if (dss.isEmpty() && db.isEmpty()) {
            message = "Aucune donnée DSS ni locale sur cette période.";
        } else if (aligned) {
            message = "Aligné — chaque créneau DSS est présent en base avec les mêmes entrées/sorties.";
        } else {
            message = "Écart détecté — " + missingInDb + " créneau(x) manquant(s) en base, "
                    + valueMismatches + " valeur(s) différente(s). Lancez une synchronisation pour rattraper.";
        }

        return new ReconcileReport(
                from,
                to,
                cameras,
                aligned,
                dss.size(),
                db.size(),
                matches,
                missingInDb,
                extraInDb,
                valueMismatches,
                dssEntries,
                dbEntries,
                dssExits,
                dbExits,
                message,
                List.copyOf(diffs)
        );
    }

    private static void addDiff(List<DiffSample> diffs, String type, SlotKey key, Counts dss, Counts db) {
        if (diffs.size() >= MAX_DIFFS) {
            return;
        }
        diffs.add(new DiffSample(
                type,
                key.channelId(),
                key.date(),
                key.hour().toString(),
                dss != null ? dss.entries() : null,
                dss != null ? dss.exits() : null,
                db != null ? db.entries() : null,
                db != null ? db.exits() : null
        ));
    }

    private static SlotKey keyFromDss(Map<String, Object> row) {
        String channelId = stringVal(row.get("channelId"));
        String startTime = stringVal(row.get("startTime"));
        if (!StringUtils.hasText(channelId) || !StringUtils.hasText(startTime)) {
            return null;
        }
        if (!PassengerFlowClient.isMainVideoChannelCode(channelId)) {
            return null;
        }
        LocalDateTime start = LocalDateTime.parse(startTime, DSS_DT);
        return new SlotKey(channelId, start.toLocalDate(), start.toLocalTime());
    }

    private static Counts countsFromDss(Map<String, Object> row) {
        return new Counts(intVal(row.get("in")), intVal(row.get("out")));
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    record SlotKey(String channelId, LocalDate date, LocalTime hour) {
    }

    record Counts(int entries, int exits) {
        int total() {
            return entries + exits;
        }

        Counts plus(Counts other) {
            return new Counts(entries + other.entries, exits + other.exits);
        }
    }

    public record DiffSample(
            String type,
            String channelId,
            LocalDate date,
            String hour,
            Integer dssIn,
            Integer dssOut,
            Integer dbIn,
            Integer dbOut
    ) {
    }

    public record ReconcileReport(
            LocalDate from,
            LocalDate to,
            int cameras,
            boolean aligned,
            int dssSlots,
            int dbSlots,
            int matches,
            int missingInDb,
            int extraInDb,
            int valueMismatches,
            long dssEntries,
            long dbEntries,
            long dssExits,
            long dbExits,
            String message,
            List<DiffSample> diffs
    ) {
    }
}
