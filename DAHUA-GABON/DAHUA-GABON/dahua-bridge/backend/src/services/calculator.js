/**
 * Moteur de calcul des heures travaillées
 *
 * Pour une journée donnée et un employé donné :
 *   1. Récupère les badges (DSS + corrections manuelles)
 *   2. Paire entrées/sorties
 *   3. Calcule heures régulières, OT, nuit, fériés
 *   4. Sauvegarde dans daily_summaries
 */

const dayjs = require('dayjs');
const duration = require('dayjs/plugin/duration');
const isBetween = require('dayjs/plugin/isBetween');
dayjs.extend(duration);
dayjs.extend(isBetween);

const { db } = require('../db');
const logger  = require('./logger');

// ── Chargement des règles configurées ────────────────────────
async function loadRules() {
  const rows = await db('work_rules').select('key', 'value');
  return Object.fromEntries(rows.map(r => [r.key, r.value]));
}

// ── Récupère le shift d'un employé pour une date ─────────────
async function getShiftForEmployee(personId, date) {
  // Cherche un shift assigné valide pour cette date
  const assigned = await db('employee_shifts')
    .where('person_id', personId)
    .where('effective_from', '<=', date)
    .where(function() {
      this.whereNull('effective_to').orWhere('effective_to', '>=', date);
    })
    .join('shifts', 'employee_shifts.shift_id', 'shifts.id')
    .select('shifts.*')
    .first();

  if (assigned) return assigned;

  // Sinon, shift par défaut
  return db('shifts').where('is_default', true).first();
}

// ── Vérifie si une date est un jour férié ────────────────────
async function isHoliday(date) {
  const d = dayjs(date);
  const mmdd = d.format('MM-DD');

  const holidays = await db('holidays').select('date', 'is_recurring');
  return holidays.some(h => {
    if (h.is_recurring) {
      return dayjs(h.date).format('MM-DD') === mmdd;
    }
    return dayjs(h.date).format('YYYY-MM-DD') === d.format('YYYY-MM-DD');
  });
}

// ── Paire les badges entrée/sortie ───────────────────────────
function pairBadges(badges) {
  // Trier par heure
  const sorted = [...badges].sort((a, b) =>
    new Date(a.swipe_time) - new Date(b.swipe_time)
  );

  const pairs = [];
  let lastEntry = null;

  for (const badge of sorted) {
    const type = String(badge.event_type);
    if (type === '1' || type === '4') {
      // Entrée
      if (lastEntry) {
        // Deux entrées consécutives → on ferme la précédente sans sortie
        pairs.push({ entry: lastEntry, exit: null });
      }
      lastEntry = badge;
    } else if (type === '2' || type === '3') {
      // Sortie
      if (lastEntry) {
        pairs.push({ entry: lastEntry, exit: badge });
        lastEntry = null;
      } else {
        // Sortie sans entrée
        pairs.push({ entry: null, exit: badge });
      }
    }
  }

  if (lastEntry) pairs.push({ entry: lastEntry, exit: null });
  return pairs;
}

// ── Calcule les minutes dans la plage nuit ───────────────────
function nightMinutes(start, end, nightStart, nightEnd) {
  // nightStart/nightEnd au format "HH:mm" (peut chevaucher minuit)
  const date  = dayjs(start).format('YYYY-MM-DD');
  const ns    = dayjs(`${date} ${nightStart}`);
  const ne    = nightStart > nightEnd
    ? dayjs(`${date} ${nightEnd}`).add(1, 'day')
    : dayjs(`${date} ${nightEnd}`);

  // Intersection [start,end] ∩ [ns, ne]
  const s = dayjs(start).isAfter(ns) ? dayjs(start) : ns;
  const e = dayjs(end).isBefore(ne)  ? dayjs(end)   : ne;

  if (e.isAfter(s)) return e.diff(s, 'minute');
  return 0;
}

// ── Calcul principal pour un employé sur une journée ─────────
async function calculateDay(personId, workDate) {
  const rules = await loadRules();
  const shift = await getShiftForEmployee(personId, workDate);
  const holiday = await isHoliday(workDate);
  const dow = dayjs(workDate).day(); // 0=dim, 1=lun...6=sam
  const isWeekend = dow === 0 || dow === 6;

  // Récupère tous les badges du jour (DSS + manuels)
  const dssBadges = await db('attendance_records')
    .where('person_id', personId)
    .whereRaw(`DATE(swipe_time) = ?`, [workDate])
    .orderBy('swipe_time');

  const manualBadges = await db('manual_badges')
    .where('person_id', personId)
    .whereRaw(`DATE(swipe_time) = ?`, [workDate])
    .orderBy('swipe_time');

  const allBadges = [...dssBadges, ...manualBadges];

  // Statut de base
  let status = 'absent';
  let firstIn = null, lastOut = null;
  let regularMins = 0, ot25Mins = 0, ot50Mins = 0, ot100Mins = 0;
  let nightMins = 0, lateMins = 0, earlyOutMins = 0;
  const anomalies = [];

  if (isWeekend && allBadges.length === 0) {
    status = 'weekend';
  } else if (holiday && allBadges.length === 0) {
    status = 'holiday';
  } else if (allBadges.length > 0) {
    const pairs = pairBadges(allBadges);

    firstIn  = pairs.find(p => p.entry)?.entry?.swipe_time || null;
    lastOut  = [...pairs].reverse().find(p => p.exit)?.exit?.swipe_time || null;

    // Vérification anomalies de base
    const hasEntry = pairs.some(p => p.entry);
    const hasExit  = pairs.some(p => p.exit);

    if (!hasEntry) anomalies.push({ type: 'missing_checkin', severity: 'error',
      description: 'Aucun badge d\'entrée trouvé' });
    if (!hasExit) anomalies.push({ type: 'missing_checkout', severity: 'warning',
      description: 'Aucun badge de sortie trouvé' });

    // Calcul des heures par paire
    let totalWorkedMins = 0;

    for (const { entry, exit } of pairs) {
      if (!entry || !exit) continue;

      const entryTime = dayjs(entry.swipe_time);
      const exitTime  = dayjs(exit.swipe_time);
      const mins = exitTime.diff(entryTime, 'minute');
      if (mins <= 0) continue;

      totalWorkedMins += mins;

      // Heures de nuit sur cette paire
      nightMins += nightMinutes(
        entry.swipe_time, exit.swipe_time,
        rules.night_start, rules.night_end
      );
    }

    // Calcul retard
    if (shift && firstIn) {
      const expectedStart = dayjs(`${workDate} ${shift.start_time}`);
      const actualStart   = dayjs(firstIn);
      const tolerance     = parseInt(rules.late_tolerance_minutes || '15', 10);
      const diffMins      = actualStart.diff(expectedStart, 'minute');
      if (diffMins > tolerance) {
        lateMins = diffMins;
        anomalies.push({ type: 'late', severity: 'info',
          description: `Retard de ${diffMins} min (arrivée ${actualStart.format('HH:mm')}, attendu ${shift.start_time})` });
      }
    }

    // Calcul sortie anticipée
    if (shift && lastOut) {
      const expectedEnd = dayjs(`${workDate} ${shift.end_time}`);
      const actualEnd   = dayjs(lastOut);
      const diffMins    = expectedEnd.diff(actualEnd, 'minute');
      if (diffMins > 0) {
        earlyOutMins = diffMins;
        anomalies.push({ type: 'early_out', severity: 'info',
          description: `Sortie anticipée de ${diffMins} min (départ ${actualEnd.format('HH:mm')}, attendu ${shift.end_time})` });
      }
    }

    // Ventilation des heures : régulières / OT
    const dailyThreshold = parseInt(rules.daily_hours_threshold || '8', 10) * 60;
    const dayType = holiday || (isWeekend && dow === 0) ? '100' : isWeekend ? '50' : 'normal';

    if (dayType === '100') {
      // Dimanche/Férié → tout en OT 100%
      ot100Mins = totalWorkedMins;
    } else if (dayType === '50') {
      // Samedi → tout en OT 50%
      ot50Mins = totalWorkedMins;
    } else {
      // Jour normal
      if (totalWorkedMins <= dailyThreshold) {
        regularMins = totalWorkedMins;
      } else {
        regularMins = dailyThreshold;
        const extraMins = totalWorkedMins - dailyThreshold;
        // Première tranche OT → 25%, au-delà → 50%
        const ot25Threshold = 2 * 60; // 2h en OT 25% puis 50%
        if (extraMins <= ot25Threshold) {
          ot25Mins = extraMins;
        } else {
          ot25Mins = ot25Threshold;
          ot50Mins = extraMins - ot25Threshold;
        }
      }
    }

    status = hasEntry && hasExit ? 'present' : 'incomplete';
    if (holiday) status = 'holiday_worked';
  }

  // Sauvegarde daily_summary
  const summary = {
    person_id:           personId,
    work_date:           workDate,
    shift_id:            shift?.id || null,
    first_in:            firstIn,
    last_out:            lastOut,
    expected_start:      shift?.start_time || null,
    expected_end:        shift?.end_time   || null,
    regular_minutes:     regularMins,
    overtime_25_minutes: ot25Mins,
    overtime_50_minutes: ot50Mins,
    overtime_100_minutes: ot100Mins,
    night_minutes:       nightMins,
    late_minutes:        lateMins,
    early_out_minutes:   earlyOutMins,
    status,
    is_holiday:          holiday,
    is_weekend:          isWeekend,
    updated_at:          new Date(),
  };

  await db('daily_summaries')
    .insert({ ...summary, created_at: new Date() })
    .onConflict(['person_id', 'work_date'])
    .merge();

  // Sauvegarde anomalies (supprime les anciennes non résolues pour ce jour)
  await db('anomalies')
    .where({ person_id: personId, work_date: workDate, resolved: false })
    .delete();

  const employee = await db('employees').where('person_id', personId).first();
  for (const a of anomalies) {
    await db('anomalies').insert({
      person_id:   personId,
      person_name: employee ? `${employee.first_name} ${employee.last_name}`.trim() || employee.person_id : personId,
      work_date:   workDate,
      type:        a.type,
      description: a.description,
      severity:    a.severity,
      created_at:  new Date(),
    });
  }

  return { summary, anomalies };
}

// ── Recalcul pour tous les employés sur une période ──────────
async function recalculatePeriod(startDate, endDate) {
  const employees = await db('employees').where('active', true).select('person_id');
  let totalDays = 0, totalAnomalies = 0;

  const start = dayjs(startDate);
  const end   = dayjs(endDate);

  for (const emp of employees) {
    let current = start;
    while (!current.isAfter(end)) {
      const { anomalies } = await calculateDay(emp.person_id, current.format('YYYY-MM-DD'));
      totalDays++;
      totalAnomalies += anomalies.length;
      current = current.add(1, 'day');
    }
  }

  logger.info(`Recalcul terminé: ${totalDays} jours, ${totalAnomalies} anomalies`);
  return { days: totalDays, anomalies: totalAnomalies };
}

// ── Calcul du résumé mensuel pour l'export Sage ──────────────
async function getMonthlySummary(year, month) {
  const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
  const endDate   = dayjs(startDate).endOf('month').format('YYYY-MM-DD');

  return db('daily_summaries')
    .join('employees', 'daily_summaries.person_id', 'employees.person_id')
    .whereBetween('work_date', [startDate, endDate])
    .whereNotIn('status', ['weekend', 'absent'])
    .groupBy('daily_summaries.person_id', 'employees.first_name', 'employees.last_name',
             'employees.card_no', 'employees.org_code')
    .select(
      'daily_summaries.person_id',
      'employees.first_name',
      'employees.last_name',
      'employees.card_no',
      'employees.org_code',
      db.raw('SUM(regular_minutes)      AS total_regular_minutes'),
      db.raw('SUM(overtime_25_minutes)  AS total_ot25_minutes'),
      db.raw('SUM(overtime_50_minutes)  AS total_ot50_minutes'),
      db.raw('SUM(overtime_100_minutes) AS total_ot100_minutes'),
      db.raw('SUM(night_minutes)        AS total_night_minutes'),
      db.raw('COUNT(CASE WHEN is_holiday AND status LIKE \'holiday%\' THEN 1 END) AS holiday_days'),
      db.raw('COUNT(CASE WHEN status = \'absent\' THEN 1 END) AS absent_days'),
    );
}

module.exports = { calculateDay, recalculatePeriod, getMonthlySummary, loadRules };
