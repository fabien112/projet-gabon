const express = require('express');
const dayjs   = require('dayjs');
const { db }  = require('../db');
const { getMonthlySummary } = require('../services/calculator');

const router = express.Router();

// ── Résumés journaliers ───────────────────────────────────────
router.get('/daily', async (req, res) => {
  const { from, to, person_id, status, page = 1, pageSize = 100 } = req.query;
  const offset = (parseInt(page) - 1) * parseInt(pageSize);

  let q = db('daily_summaries')
    .join('employees', 'daily_summaries.person_id', 'employees.person_id')
    .orderBy('work_date', 'desc')
    .select(
      'daily_summaries.*',
      'employees.first_name', 'employees.last_name',
      'employees.org_code', 'employees.card_no',
    );

  if (from)      q = q.where('work_date', '>=', from);
  if (to)        q = q.where('work_date', '<=', to);
  if (person_id) q = q.where('daily_summaries.person_id', person_id);
  if (status)    q = q.where('status', status);

  const [{ count }] = await q.clone().count('daily_summaries.id as count');
  const data = await q.limit(parseInt(pageSize)).offset(offset);

  // Convertit minutes → heures pour affichage
  const formatted = data.map(row => ({
    ...row,
    regular_hours:      +(row.regular_minutes / 60).toFixed(2),
    overtime_25_hours:  +(row.overtime_25_minutes / 60).toFixed(2),
    overtime_50_hours:  +(row.overtime_50_minutes / 60).toFixed(2),
    overtime_100_hours: +(row.overtime_100_minutes / 60).toFixed(2),
    night_hours:        +(row.night_minutes / 60).toFixed(2),
    late_hours:         +(row.late_minutes / 60).toFixed(2),
  }));

  res.json({ success: true, data: formatted, total: parseInt(count) });
});

// ── Résumé mensuel par employé ────────────────────────────────
router.get('/monthly', async (req, res) => {
  const year  = parseInt(req.query.year  || dayjs().year());
  const month = parseInt(req.query.month || dayjs().month() + 1);

  const rows = await getMonthlySummary(year, month);

  const data = rows.map(r => ({
    person_id:        r.person_id,
    name:             `${r.first_name} ${r.last_name}`.trim() || r.person_id,
    card_no:          r.card_no,
    org_code:         r.org_code,
    regular_hours:    +(r.total_regular_minutes / 60).toFixed(2),
    overtime_25:      +(r.total_ot25_minutes / 60).toFixed(2),
    overtime_50:      +(r.total_ot50_minutes / 60).toFixed(2),
    overtime_100:     +(r.total_ot100_minutes / 60).toFixed(2),
    night_hours:      +(r.total_night_minutes / 60).toFixed(2),
    holiday_days:     parseInt(r.holiday_days || 0),
    absent_days:      parseInt(r.absent_days  || 0),
  }));

  res.json({ success: true, data, year, month });
});

// ── Export Sage X3 avec heures calculées ──────────────────────
router.post('/export-sage', async (req, res) => {
  const year  = parseInt(req.query.year  || req.body?.year  || dayjs().year());
  const month = parseInt(req.query.month || req.body?.month || dayjs().month() + 1);

  const [rows, sageConfig] = await Promise.all([
    getMonthlySummary(year, month),
    db('sage_export_config').where('enabled', true).orderBy('sort_order'),
  ]);

  const periode = `${String(month).padStart(2,'0')}/${year}`;
  const lines   = ['MATRICULE;CODE_RUBRIQUE;VALEUR;PERIODE;NOM'];

  const typeMap = {
    regular:      'total_regular_minutes',
    overtime_25:  'total_ot25_minutes',
    overtime_50:  'total_ot50_minutes',
    overtime_100: 'total_ot100_minutes',
    night:        'total_night_minutes',
  };

  for (const emp of rows) {
    const name = `${emp.first_name || ''} ${emp.last_name || ''}`.trim() || emp.person_id;
    for (const cfg of sageConfig) {
      const field = typeMap[cfg.hour_type];
      if (!field) continue;
      const minutes = parseFloat(emp[field] || 0);
      if (minutes <= 0) continue;
      const hours = (minutes / 60).toFixed(2);
      lines.push(`${emp.person_id};${cfg.rubrique_code};${hours};${periode};${name}`);
    }
  }

  const csv = lines.join('\r\n');
  const filename = `SAGE_HEURES_${year}_${String(month).padStart(2,'0')}.csv`;

  const fs   = require('fs');
  const path = require('path');
  const config = require('../config');
  const exportPath = path.join(config.sage.exportPath, filename);

  try {
    fs.mkdirSync(config.sage.exportPath, { recursive: true });
    fs.writeFileSync(exportPath, '﻿' + csv, 'utf8'); // BOM pour Excel
  } catch (e) {
    // Répertoire non accessible (dev), on retourne juste le CSV
  }

  res.setHeader('Content-Type', 'text/csv; charset=utf-8');
  res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
  res.send('﻿' + csv);
});

module.exports = router;
