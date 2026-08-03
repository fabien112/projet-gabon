/**
 * Récupération des données DSS Pro :
 *   - Départements (person-groups)
 *   - Employés
 *   - Pointages (attendance records)
 */

const dayjs  = require('dayjs');
const { client, userHeaders } = require('./dss-auth');
const logger = require('./logger');

const EVENT_TYPES = { '0': 'Tous', '1': 'Entrée', '2': 'Sortie', '3': 'Sortie ext.', '4': 'Retour' };

// ── Départements ─────────────────────────────────────────────
async function getDepartments() {
  const headers = await userHeaders();
  const { data } = await client.get('/obms/api/v1.1/acs/person-group/list', { headers });
  if (data.code !== 1000) throw new Error(`getDepartments: ${data.desc}`);
  return (data.data?.results || []).map(d => ({
    orgCode:       d.orgCode,
    parentOrgCode: d.parentOrgCode || null,
    orgName:       d.orgName,
    childNum:      parseInt(d.childNum || '0', 10),
  }));
}

// ── Employés ─────────────────────────────────────────────────
async function getEmployees(orgCode = '001', pageSize = 1000) {
  const headers = await userHeaders();
  let page = 1, all = [];

  while (true) {
    const { data } = await client.get('/obms/api/v1.1/acs/person/page', {
      headers,
      params: { page, pageSize, orgCode, containChild: 1 },
    });
    if (data.code !== 1000) throw new Error(`getEmployees: ${data.desc}`);

    const rows = data.data?.pageData || [];
    all.push(...rows);
    if (all.length >= parseInt(data.data?.totalCount || '0', 10)) break;
    page++;
  }

  return all.map(p => {
    const b = p.baseInfo || {};
    return {
      personId:   b.personId,
      firstName:  b.firstName || '',
      lastName:   b.lastName  || '',
      orgCode:    b.orgCode,
      orgName:    b.orgName,
      cardNo:     (p.cardList?.[0]?.cardNo) || '',
      jobTitle:   b.jobTitle || '',
    };
  });
}

// ── Pointages ────────────────────────────────────────────────
async function getAttendanceRecords({ deptId = '001', startTime, endTime, eventType = '0', pageSize = 1000 } = {}) {
  const headers = await userHeaders();

  // DSS Pro limite à 1 mois par requête
  const start = startTime || dayjs().subtract(30, 'day').format('YYYY-MM-DD HH:mm:ss');
  const end   = endTime   || dayjs().format('YYYY-MM-DD HH:mm:ss');

  let page = 1, all = [];

  while (true) {
    const { data } = await client.get(
      '/obms/api/v1.0/attendance/swiping-card-report/page',
      {
        headers,
        params: { startTime: start, endTime: end, deptId, eventType, page, pageSize },
        timeout: 30000,
      }
    );

    if (data.code !== 1000) {
      logger.warn(`getAttendanceRecords page ${page}: code=${data.code} ${data.desc}`);
      break;
    }

    const rows = data.data?.pageData || [];
    const total = parseInt(data.data?.total || '0', 10);
    all.push(...rows);
    logger.info(`Attendance page ${page}: ${rows.length} records (total ${total})`);

    if (all.length >= total || rows.length === 0) break;
    page++;
  }

  return all.map(r => ({
    personId:    r.personId,
    personName:  r.name,
    orgCode:     r.deptId   || deptId,
    deptName:    r.deptName || '',
    swipeTime:   r.swipeTime,
    eventType:   r.eventType || '0',
    eventName:   r.eventName || EVENT_TYPES[r.eventType] || '',
    deviceName:  r.deviceName  || '',
    channelName: r.channelName || '',
  }));
}

// ── Rapport présence mensuel ─────────────────────────────────
async function getMonthlyAttendance(year, month, deptId = '001') {
  const start = dayjs(`${year}-${String(month).padStart(2, '0')}-01`).format('YYYY-MM-DD 00:00:00');
  const end   = dayjs(start).endOf('month').format('YYYY-MM-DD 23:59:59');
  return getAttendanceRecords({ deptId, startTime: start, endTime: end });
}

module.exports = { getDepartments, getEmployees, getAttendanceRecords, getMonthlyAttendance };
