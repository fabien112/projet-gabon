/**
 * Job de synchronisation DSS → PostgreSQL
 *
 * Deux cadences :
 *   - Sync complète  : toutes les N minutes (depts + employés + historique)
 *   - Sync temps réel: toutes les 30s      (badges des 2 dernières minutes)
 *     → émet 'new_badges' sur l'event-bus quand de nouveaux pointages arrivent
 */

const cron       = require('node-cron');
const dayjs      = require('dayjs');
const { db }     = require('../db');
const { getDepartments, getEmployees, getAttendanceRecords } = require('../services/dss-attendance');
const { calculateDay } = require('../services/calculator');
const eventBus   = require('../services/event-bus');
const logger     = require('../services/logger');
const config     = require('../config');

let _lastFullSync     = null;
let _lastRealtimeSync = null;

// ── Sync complète (depts + employés + historique) ────────────
async function syncDepartments() {
  const depts = await getDepartments();
  for (const d of depts) {
    await db('departments').insert({
      org_code:        d.orgCode,
      parent_org_code: d.parentOrgCode,
      org_name:        d.orgName,
      child_num:       d.childNum,
    }).onConflict('org_code').merge();
  }
  logger.info(`Sync depts: ${depts.length} enregistrés`);
}

async function syncEmployees() {
  const employees = await getEmployees('001');
  for (const e of employees) {
    await db('employees').insert({
      person_id:  e.personId,
      org_code:   e.orgCode,
      first_name: e.firstName,
      last_name:  e.lastName,
      card_no:    e.cardNo,
      job_title:  e.jobTitle,
    }).onConflict('person_id').merge();
  }
  logger.info(`Sync employees: ${employees.length} enregistrés`);
}

async function syncAttendance() {
  const since = _lastFullSync
    ? dayjs(_lastFullSync).format('YYYY-MM-DD HH:mm:ss')
    : dayjs().subtract(30, 'day').format('YYYY-MM-DD HH:mm:ss');
  const until = dayjs().format('YYYY-MM-DD HH:mm:ss');

  const records = await getAttendanceRecords({ deptId: '001', startTime: since, endTime: until });
  let newCount = 0;

  for (const r of records) {
    try {
      await db('attendance_records').insert({
        person_id:    r.personId,
        person_name:  r.personName,
        org_code:     r.orgCode,
        dept_name:    r.deptName,
        swipe_time:   r.swipeTime,
        event_type:   r.eventType,
        event_name:   r.eventName,
        device_name:  r.deviceName,
        channel_name: r.channelName,
      });
      newCount++;
    } catch (e) {
      if (!e.message?.includes('unique')) logger.warn(`Insert attendance: ${e.message}`);
    }
  }

  _lastFullSync = new Date();
  logger.info(`Sync attendance: ${newCount} nouveaux sur ${records.length} reçus`);

  // Recalcule les journées affectées par les nouveaux badges
  if (newCount > 0) {
    const affectedDays = new Set();
    for (const r of records) {
      affectedDays.add(`${r.personId}|${dayjs(r.swipeTime).format('YYYY-MM-DD')}`);
    }
    for (const key of affectedDays) {
      const [personId, workDate] = key.split('|');
      try { await calculateDay(personId, workDate); } catch (e) {
        logger.warn(`calculateDay ${personId} ${workDate}: ${e.message}`);
      }
    }
  }

  return { total: records.length, new: newCount };
}

async function runSync() {
  const start = Date.now();
  let status = 'success', message = '', recordsNew = 0;
  try {
    await syncDepartments();
    await syncEmployees();
    const result = await syncAttendance();
    recordsNew = result.new;
    message = `OK en ${Date.now() - start}ms`;
  } catch (err) {
    status  = 'error';
    message = err.message;
    logger.error(`Sync error: ${err.message}`);
  }
  await db('sync_log').insert({ status, records_new: recordsNew, message });
  return { status, recordsNew, message };
}

// ── Sync temps réel (badges récents → push SSE) ──────────────
async function syncRealtime() {
  // Ne demande que les 2 dernières minutes à DSS (requête très légère)
  const lookback = 2; // minutes
  const since = dayjs().subtract(lookback, 'minute').format('YYYY-MM-DD HH:mm:ss');
  const until = dayjs().format('YYYY-MM-DD HH:mm:ss');

  let records;
  try {
    records = await getAttendanceRecords({ deptId: '001', startTime: since, endTime: until });
  } catch (err) {
    logger.warn(`Realtime sync error: ${err.message}`);
    return;
  }

  const newBadges = [];
  for (const r of records) {
    try {
      await db('attendance_records').insert({
        person_id:    r.personId,
        person_name:  r.personName,
        org_code:     r.orgCode,
        dept_name:    r.deptName,
        swipe_time:   r.swipeTime,
        event_type:   r.eventType,
        event_name:   r.eventName,
        device_name:  r.deviceName,
        channel_name: r.channelName,
      });
      newBadges.push(r);
    } catch (e) {
      // contrainte unique = déjà connu, pas un badge nouveau
    }
  }

  if (newBadges.length > 0) {
    logger.info(`Temps réel: ${newBadges.length} nouveau(x) badge(s)`);
    eventBus.emit('new_badges', newBadges);

    // Recalcule les journées en temps réel
    const affectedDays = new Set(
      newBadges.map(b => `${b.personId}|${dayjs(b.swipeTime).format('YYYY-MM-DD')}`)
    );
    for (const key of affectedDays) {
      const [personId, workDate] = key.split('|');
      try { await calculateDay(personId, workDate); } catch (e) {
        logger.warn(`RT calculateDay: ${e.message}`);
      }
    }
  }

  _lastRealtimeSync = new Date();
}

// ── Démarrage des deux schedulers ────────────────────────────
function startScheduler() {
  const fullInterval     = config.dss.syncInterval;
  const realtimeInterval = config.dss.realtimeInterval;

  logger.info(`Scheduler démarré — sync complète toutes les ${fullInterval} min, temps réel toutes les ${realtimeInterval}s`);

  // Sync complète immédiate au démarrage
  runSync();

  // Sync complète périodique
  cron.schedule(`*/${fullInterval} * * * *`, () => {
    logger.info('Sync complète...');
    runSync();
  });

  // Sync temps réel (utilise setInterval car cron ne supporte pas les secondes facilement)
  setInterval(() => {
    syncRealtime();
  }, realtimeInterval * 1000);
}

module.exports = { startScheduler, runSync, syncAttendance, syncRealtime };
