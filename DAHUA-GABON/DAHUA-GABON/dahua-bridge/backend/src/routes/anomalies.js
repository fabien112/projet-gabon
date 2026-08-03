const express = require('express');
const dayjs   = require('dayjs');
const { db }  = require('../db');
const { calculateDay } = require('../services/calculator');

const router = express.Router();

// ── Liste des anomalies ───────────────────────────────────────
router.get('/', async (req, res) => {
  const { from, to, type, resolved, person_id, page = 1, pageSize = 50 } = req.query;
  const offset = (parseInt(page) - 1) * parseInt(pageSize);

  let query = db('anomalies').orderBy('work_date', 'desc').orderBy('severity', 'asc');

  if (from)      query = query.where('work_date', '>=', from);
  if (to)        query = query.where('work_date', '<=', to);
  if (type)      query = query.where('type', type);
  if (person_id) query = query.where('person_id', person_id);
  if (resolved !== undefined) query = query.where('resolved', resolved === 'true');

  const [{ count }] = await query.clone().count('id as count');
  const data = await query.limit(parseInt(pageSize)).offset(offset);

  res.json({ success: true, data, total: parseInt(count), page: parseInt(page) });
});

// ── Stats anomalies ───────────────────────────────────────────
router.get('/stats', async (req, res) => {
  const today = dayjs().format('YYYY-MM-DD');
  const weekStart = dayjs().startOf('week').format('YYYY-MM-DD');

  const [total]    = await db('anomalies').where('resolved', false).count('id as count');
  const [today_c]  = await db('anomalies').where('work_date', today).where('resolved', false).count('id as count');
  const [week_c]   = await db('anomalies').where('work_date', '>=', weekStart).where('resolved', false).count('id as count');
  const byType     = await db('anomalies').where('resolved', false)
    .groupBy('type').select('type', db.raw('COUNT(*) as count'));

  res.json({ success: true, data: {
    total:   parseInt(total.count),
    today:   parseInt(today_c.count),
    week:    parseInt(week_c.count),
    by_type: byType,
  }});
});

// ── Résoudre une anomalie ─────────────────────────────────────
router.put('/:id/resolve', async (req, res) => {
  const { resolution_note, resolved_by } = req.body;
  await db('anomalies').where('id', req.params.id).update({
    resolved:         true,
    resolved_by:      resolved_by || 'RH',
    resolved_at:      new Date(),
    resolution_note:  resolution_note || '',
  });
  res.json({ success: true });
});

// ── Résoudre toutes les anomalies d'un employé/jour ───────────
router.put('/resolve-day', async (req, res) => {
  const { person_id, work_date, resolution_note } = req.body;
  await db('anomalies')
    .where({ person_id, work_date, resolved: false })
    .update({ resolved: true, resolved_by: 'RH', resolved_at: new Date(), resolution_note });
  res.json({ success: true });
});

// ── Badges manuels (corrections RH) ──────────────────────────
router.get('/manual-badges', async (req, res) => {
  const { person_id, from, to } = req.query;
  let q = db('manual_badges').orderBy('swipe_time', 'desc');
  if (person_id) q = q.where('person_id', person_id);
  if (from) q = q.where('swipe_time', '>=', from);
  if (to)   q = q.where('swipe_time', '<=', to);
  res.json({ success: true, data: await q });
});

router.post('/manual-badges', async (req, res) => {
  const { person_id, swipe_time, event_type, reason, created_by } = req.body;

  // Récupère le nom de l'employé
  const emp = await db('employees').where('person_id', person_id).first();

  await db('manual_badges').insert({
    person_id,
    person_name: emp ? `${emp.first_name} ${emp.last_name}`.trim() || person_id : person_id,
    swipe_time,
    event_type,
    reason,
    created_by: created_by || 'RH',
    created_at: new Date(),
  });

  // Recalcule la journée concernée
  const workDate = dayjs(swipe_time).format('YYYY-MM-DD');
  await calculateDay(person_id, workDate);

  res.json({ success: true });
});

router.delete('/manual-badges/:id', async (req, res) => {
  const badge = await db('manual_badges').where('id', req.params.id).first();
  if (!badge) return res.status(404).json({ success: false, error: 'Not found' });

  await db('manual_badges').where('id', req.params.id).delete();

  // Recalcule la journée
  const workDate = dayjs(badge.swipe_time).format('YYYY-MM-DD');
  await calculateDay(badge.person_id, workDate);

  res.json({ success: true });
});

module.exports = router;
