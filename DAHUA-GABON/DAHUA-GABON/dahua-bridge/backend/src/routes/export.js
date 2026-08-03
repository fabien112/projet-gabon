const express  = require('express');
const dayjs    = require('dayjs');
const { db }   = require('../db');
const { exportMonth, exportToCsv } = require('../services/sage-export');
const { runSync } = require('../jobs/sync');
const router   = express.Router();

// POST /api/export/sage?year=2024&month=5
router.post('/sage', async (req, res) => {
  const year  = parseInt(req.query.year  || dayjs().year(),  10);
  const month = parseInt(req.query.month || dayjs().month() + 1, 10);

  const start = dayjs(`${year}-${String(month).padStart(2, '0')}-01`).format('YYYY-MM-DD 00:00:00');
  const end   = dayjs(start).endOf('month').format('YYYY-MM-DD 23:59:59');

  try {
    const records = await db('attendance_records')
      .where('swipe_time', '>=', start)
      .where('swipe_time', '<=', end)
      .orderBy('swipe_time');

    if (records.length === 0) {
      return res.json({ success: false, message: 'Aucun pointage pour cette période.' });
    }

    const filePath = await exportMonth(records, year, month);

    // Marquer comme exportés
    await db('attendance_records')
      .where('swipe_time', '>=', start)
      .where('swipe_time', '<=', end)
      .update({ exported_sage: true });

    res.json({
      success:   true,
      filePath,
      records:   records.length,
      period:    `${String(month).padStart(2, '0')}/${year}`,
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// GET /api/export/download?file=POINTAGES_2024_05.csv
router.get('/download', async (req, res) => {
  const { exportPath } = require('../config').sage;
  const file = req.query.file;
  if (!file || file.includes('..')) return res.status(400).send('Fichier invalide');
  const filePath = require('path').join(exportPath, file);
  if (!require('fs').existsSync(filePath)) return res.status(404).send('Fichier introuvable');
  res.download(filePath);
});

// POST /api/export/sync — déclencher une synchronisation manuelle
router.post('/sync', async (req, res) => {
  try {
    const result = await runSync();
    res.json({ success: true, ...result });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// GET /api/export/sync-log — historique des synchronisations
router.get('/sync-log', async (req, res) => {
  try {
    const logs = await db('sync_log').orderBy('synced_at', 'desc').limit(50);
    res.json({ success: true, data: logs });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
