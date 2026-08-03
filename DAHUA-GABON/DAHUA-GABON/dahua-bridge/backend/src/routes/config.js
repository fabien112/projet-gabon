const express = require('express');
const dayjs   = require('dayjs');
const { db }  = require('../db');
const { recalculatePeriod } = require('../services/calculator');

const router = express.Router();

// ── Shifts ────────────────────────────────────────────────────
router.get('/shifts', async (req, res) => {
  const data = await db('shifts').orderBy('id');
  res.json({ success: true, data });
});

router.post('/shifts', async (req, res) => {
  const { name, start_time, end_time, crosses_midnight, days_of_week, is_default } = req.body;
  if (is_default) await db('shifts').update({ is_default: false });
  const [id] = await db('shifts').insert(
    { name, start_time, end_time, crosses_midnight: !!crosses_midnight,
      days_of_week: days_of_week || '1,2,3,4,5', is_default: !!is_default },
    ['id']
  );
  res.json({ success: true, data: { id: id.id } });
});

router.put('/shifts/:id', async (req, res) => {
  const { name, start_time, end_time, crosses_midnight, days_of_week, is_default } = req.body;
  if (is_default) await db('shifts').whereNot('id', req.params.id).update({ is_default: false });
  await db('shifts').where('id', req.params.id).update(
    { name, start_time, end_time, crosses_midnight: !!crosses_midnight,
      days_of_week, is_default: !!is_default, updated_at: new Date() }
  );
  res.json({ success: true });
});

router.delete('/shifts/:id', async (req, res) => {
  await db('employee_shifts').where('shift_id', req.params.id).delete();
  await db('shifts').where('id', req.params.id).delete();
  res.json({ success: true });
});

// ── Assignation shift → employé ───────────────────────────────
router.get('/employee-shifts', async (req, res) => {
  const data = await db('employee_shifts')
    .join('employees', 'employee_shifts.person_id', 'employees.person_id')
    .join('shifts', 'employee_shifts.shift_id', 'shifts.id')
    .select('employee_shifts.*', 'employees.first_name', 'employees.last_name', 'shifts.name as shift_name');
  res.json({ success: true, data });
});

router.post('/employee-shifts', async (req, res) => {
  const { person_id, shift_id, effective_from, effective_to } = req.body;
  // Ferme les assignations actives précédentes
  await db('employee_shifts')
    .where({ person_id })
    .whereNull('effective_to')
    .update({ effective_to: dayjs(effective_from).subtract(1, 'day').format('YYYY-MM-DD') });
  await db('employee_shifts').insert({ person_id, shift_id, effective_from, effective_to: effective_to || null });
  res.json({ success: true });
});

// ── Règles de calcul ──────────────────────────────────────────
router.get('/rules', async (req, res) => {
  const data = await db('work_rules').orderBy('category').orderBy('id');
  res.json({ success: true, data });
});

router.put('/rules', async (req, res) => {
  const updates = req.body; // { key: value, ... }
  for (const [key, value] of Object.entries(updates)) {
    await db('work_rules').where('key', key).update({ value: String(value) });
  }
  res.json({ success: true });
});

// ── Jours fériés ──────────────────────────────────────────────
router.get('/holidays', async (req, res) => {
  const data = await db('holidays').orderBy('date');
  res.json({ success: true, data });
});

router.post('/holidays', async (req, res) => {
  const { date, name, is_recurring } = req.body;
  const [row] = await db('holidays').insert(
    { date, name, is_recurring: !!is_recurring }, ['id']
  );
  res.json({ success: true, data: { id: row.id } });
});

router.put('/holidays/:id', async (req, res) => {
  const { date, name, is_recurring } = req.body;
  await db('holidays').where('id', req.params.id).update({ date, name, is_recurring: !!is_recurring });
  res.json({ success: true });
});

router.delete('/holidays/:id', async (req, res) => {
  await db('holidays').where('id', req.params.id).delete();
  res.json({ success: true });
});

// ── Config export Sage ────────────────────────────────────────
router.get('/sage', async (req, res) => {
  const data = await db('sage_export_config').orderBy('sort_order');
  res.json({ success: true, data });
});

router.put('/sage', async (req, res) => {
  const items = req.body; // [{ hour_type, rubrique_code, rubrique_label, enabled }]
  for (const item of items) {
    await db('sage_export_config')
      .where('hour_type', item.hour_type)
      .update({ rubrique_code: item.rubrique_code, rubrique_label: item.rubrique_label, enabled: item.enabled });
  }
  res.json({ success: true });
});

// ── Recalcul manuel ───────────────────────────────────────────
router.post('/recalculate', async (req, res) => {
  const { start_date, end_date } = req.body;
  const start = start_date || dayjs().subtract(30, 'day').format('YYYY-MM-DD');
  const end   = end_date   || dayjs().format('YYYY-MM-DD');
  const result = await recalculatePeriod(start, end);
  res.json({ success: true, data: result });
});

module.exports = router;
