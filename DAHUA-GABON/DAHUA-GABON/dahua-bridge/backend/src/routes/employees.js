const express = require('express');
const { db }  = require('../db');
const router  = express.Router();

// GET /api/employees
router.get('/', async (req, res) => {
  const { deptId, search, page = 1, pageSize = 100 } = req.query;
  try {
    let q = db('employees as e')
      .leftJoin('departments as d', 'e.org_code', 'd.org_code')
      .select('e.*', 'd.org_name')
      .where('e.active', true)
      .orderBy('e.last_name');

    if (deptId)  q = q.where('e.org_code', deptId);
    if (search)  q = q.whereRaw(
      "LOWER(e.first_name || ' ' || e.last_name) LIKE ?",
      [`%${search.toLowerCase()}%`]
    );

    const total = await q.clone().count('e.person_id as c').first();
    const rows  = await q.limit(pageSize).offset((page - 1) * pageSize);
    res.json({ success: true, total: Number(total.c), data: rows });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// GET /api/employees/departments
router.get('/departments', async (req, res) => {
  try {
    const depts = await db('departments').orderBy('org_code');
    res.json({ success: true, data: depts });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// GET /api/employees/:id/attendance?month=YYYY-MM
router.get('/:id/attendance', async (req, res) => {
  const { id } = req.params;
  const month  = req.query.month || require('dayjs')().format('YYYY-MM');
  const start  = `${month}-01 00:00:00`;
  const end    = require('dayjs')(start).endOf('month').format('YYYY-MM-DD 23:59:59');

  try {
    const records = await db('attendance_records')
      .where('person_id', id)
      .where('swipe_time', '>=', start)
      .where('swipe_time', '<=', end)
      .orderBy('swipe_time');
    res.json({ success: true, data: records });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
