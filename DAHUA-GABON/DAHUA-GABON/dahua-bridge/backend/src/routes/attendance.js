const express = require('express');
const dayjs   = require('dayjs');
const { db }  = require('../db');
const router  = express.Router();

// GET /api/attendance?startDate=&endDate=&deptId=&personId=&page=&pageSize=
router.get('/', async (req, res) => {
  const {
    startDate, endDate,
    deptId, personId,
    eventType,
    page     = 1,
    pageSize = 50,
  } = req.query;

  try {
    let q = db('attendance_records as a')
      .leftJoin('employees as e', 'a.person_id', 'e.person_id')
      .leftJoin('departments as d', 'a.org_code', 'd.org_code')
      .select(
        'a.id', 'a.person_id', 'a.person_name',
        'a.swipe_time', 'a.event_type', 'a.event_name',
        'a.dept_name', 'a.device_name', 'a.exported_sage',
        'e.first_name', 'e.last_name', 'e.card_no', 'e.job_title',
        'd.org_name'
      )
      .orderBy('a.swipe_time', 'desc');

    if (startDate) q = q.where('a.swipe_time', '>=', dayjs(startDate).toDate());
    if (endDate)   q = q.where('a.swipe_time', '<=', dayjs(endDate).endOf('day').toDate());
    if (deptId)    q = q.where('a.org_code', deptId);
    if (personId)  q = q.where('a.person_id', personId);
    if (eventType) q = q.where('a.event_type', eventType);

    const total   = await q.clone().count('a.id as count').first();
    const records = await q.limit(pageSize).offset((page - 1) * pageSize);

    res.json({ success: true, total: Number(total.count), page: Number(page), data: records });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// GET /api/attendance/stats — résumé du jour
router.get('/stats', async (req, res) => {
  const today     = dayjs().format('YYYY-MM-DD');
  const startDay  = `${today} 00:00:00`;
  const endDay    = `${today} 23:59:59`;

  try {
    const [present, late, absences, total_emp] = await Promise.all([
      db('attendance_records').where('swipe_time', '>=', startDay).where('swipe_time', '<=', endDay).where('event_type', '1').countDistinct('person_id as c').first(),
      db('attendance_records').where('swipe_time', '>', `${today} 08:00:00`).where('event_type', '1').countDistinct('person_id as c').first(),
      db('employees').where('active', true).count('person_id as c').first(),
      db('employees').where('active', true).count('person_id as c').first(),
    ]);

    const presentCount  = Number(present?.c || 0);
    const totalEmp      = Number(total_emp?.c || 0);

    res.json({
      success: true,
      data: {
        present:   presentCount,
        absent:    totalEmp - presentCount,
        late:      Number(late?.c || 0),
        total:     totalEmp,
        syncedAt:  await db('sync_log').orderBy('synced_at', 'desc').first().then(r => r?.synced_at),
      },
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// GET /api/attendance/daily-summary?date=YYYY-MM-DD&deptId=
router.get('/daily-summary', async (req, res) => {
  const date   = req.query.date || dayjs().format('YYYY-MM-DD');
  const deptId = req.query.deptId;

  const start = `${date} 00:00:00`;
  const end   = `${date} 23:59:59`;

  try {
    let q = db('attendance_records as a')
      .where('a.swipe_time', '>=', start)
      .where('a.swipe_time', '<=', end)
      .select(
        'a.person_id', 'a.person_name', 'a.dept_name',
        db.raw('MIN(CASE WHEN event_type = \'1\' THEN swipe_time END) as first_in'),
        db.raw('MAX(CASE WHEN event_type = \'2\' THEN swipe_time END) as last_out'),
        db.raw('COUNT(*) as total_swipes')
      )
      .groupBy('a.person_id', 'a.person_name', 'a.dept_name');

    if (deptId) q = q.where('a.org_code', deptId);

    const rows = await q;
    res.json({ success: true, date, data: rows });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
