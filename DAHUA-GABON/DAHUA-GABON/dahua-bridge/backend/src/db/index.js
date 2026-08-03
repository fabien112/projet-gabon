const knex   = require('knex');
const config = require('../config');
const logger = require('../services/logger');

const db = knex({
  client: 'pg',
  connection: config.db,
  pool: { min: 1, max: 5 },
  acquireConnectionTimeout: 10000,
});

async function migrate() {
  // ── Tables existantes ──────────────────────────────────────
  if (!await db.schema.hasTable('departments')) {
    await db.schema.createTable('departments', t => {
      t.string('org_code').primary();
      t.string('parent_org_code');
      t.string('org_name').notNullable();
      t.integer('child_num').defaultTo(0);
      t.timestamps(true, true);
    });
  }

  if (!await db.schema.hasTable('employees')) {
    await db.schema.createTable('employees', t => {
      t.string('person_id').primary();
      t.string('org_code').references('org_code').inTable('departments');
      t.string('first_name');
      t.string('last_name');
      t.string('card_no');
      t.string('department');
      t.string('job_title');
      t.boolean('active').defaultTo(true);
      t.timestamps(true, true);
    });
  }

  if (!await db.schema.hasTable('attendance_records')) {
    await db.schema.createTable('attendance_records', t => {
      t.increments('id');
      t.string('person_id').references('person_id').inTable('employees');
      t.string('person_name');
      t.string('org_code');
      t.string('dept_name');
      t.timestamp('swipe_time').notNullable();
      t.string('event_type');
      t.string('event_name');
      t.string('device_name');
      t.string('channel_name');
      t.boolean('exported_sage').defaultTo(false);
      t.timestamps(true, true);
      t.unique(['person_id', 'swipe_time', 'event_type']);
    });
  }

  if (!await db.schema.hasTable('sync_log')) {
    await db.schema.createTable('sync_log', t => {
      t.increments('id');
      t.timestamp('synced_at').defaultTo(db.fn.now());
      t.string('status');
      t.integer('records_new').defaultTo(0);
      t.text('message');
    });
  }

  // ── Horaires de travail ────────────────────────────────────
  if (!await db.schema.hasTable('shifts')) {
    await db.schema.createTable('shifts', t => {
      t.increments('id');
      t.string('name').notNullable();          // "Matin", "Après-midi", "Nuit"
      t.string('start_time').notNullable();    // "07:00"
      t.string('end_time').notNullable();      // "15:00"
      t.boolean('crosses_midnight').defaultTo(false);
      t.string('days_of_week').defaultTo('1,2,3,4,5'); // Lun-Ven
      t.boolean('is_default').defaultTo(false);
      t.timestamps(true, true);
    });
    // Shift par défaut
    await db('shifts').insert({
      name: 'Journée standard', start_time: '08:00', end_time: '17:00',
      crosses_midnight: false, days_of_week: '1,2,3,4,5', is_default: true,
    });
  }

  // ── Assignation shift → employé ────────────────────────────
  if (!await db.schema.hasTable('employee_shifts')) {
    await db.schema.createTable('employee_shifts', t => {
      t.increments('id');
      t.string('person_id').references('person_id').inTable('employees');
      t.integer('shift_id').references('id').inTable('shifts');
      t.date('effective_from').notNullable();
      t.date('effective_to');
    });
  }

  // ── Règles de calcul ───────────────────────────────────────
  if (!await db.schema.hasTable('work_rules')) {
    await db.schema.createTable('work_rules', t => {
      t.increments('id');
      t.string('key').unique().notNullable();
      t.string('value').notNullable();
      t.string('label');
      t.string('category');  // 'overtime' | 'night' | 'general'
    });
    await db('work_rules').insert([
      { key: 'daily_hours_threshold',   value: '8',     label: 'Seuil heures sup/jour (h)',    category: 'overtime' },
      { key: 'weekly_hours_threshold',  value: '40',    label: 'Seuil heures sup/semaine (h)', category: 'overtime' },
      { key: 'overtime_rate_25',        value: '1.25',  label: 'Taux OT 25%',                  category: 'overtime' },
      { key: 'overtime_rate_50',        value: '1.50',  label: 'Taux OT 50%',                  category: 'overtime' },
      { key: 'overtime_rate_100',       value: '2.00',  label: 'Taux OT 100% (dim/férié)',     category: 'overtime' },
      { key: 'night_start',             value: '22:00', label: 'Début heures de nuit',          category: 'night'    },
      { key: 'night_end',               value: '06:00', label: 'Fin heures de nuit',            category: 'night'    },
      { key: 'night_rate',              value: '1.30',  label: 'Taux heures de nuit',           category: 'night'    },
      { key: 'late_tolerance_minutes',  value: '15',    label: 'Tolérance retard (min)',        category: 'general'  },
      { key: 'min_break_minutes',       value: '30',    label: 'Pause minimum (min)',           category: 'general'  },
      { key: 'work_week_start',         value: '1',     label: 'Début semaine (1=Lun, 0=Dim)', category: 'general'  },
    ]);
  }

  // ── Jours fériés ───────────────────────────────────────────
  if (!await db.schema.hasTable('holidays')) {
    await db.schema.createTable('holidays', t => {
      t.increments('id');
      t.date('date').notNullable();
      t.string('name').notNullable();
      t.boolean('is_recurring').defaultTo(true);  // répète chaque année
    });
    // Jours fériés Gabon
    await db('holidays').insert([
      { date: '2026-01-01', name: 'Jour de l\'An',              is_recurring: true },
      { date: '2026-03-12', name: 'Anniversaire de la République', is_recurring: true },
      { date: '2026-04-17', name: 'Vendredi Saint',             is_recurring: false },
      { date: '2026-05-01', name: 'Fête du Travail',            is_recurring: true },
      { date: '2026-06-05', name: 'Pentecôte',                  is_recurring: false },
      { date: '2026-08-15', name: 'Assomption',                 is_recurring: true },
      { date: '2026-08-16', name: 'Fête Nationale du Gabon',    is_recurring: true },
      { date: '2026-08-17', name: 'Fête Nationale du Gabon (2)', is_recurring: true },
      { date: '2026-11-01', name: 'Toussaint',                  is_recurring: true },
      { date: '2026-12-25', name: 'Noël',                       is_recurring: true },
    ]);
  }

  // ── Résumés journaliers calculés ───────────────────────────
  if (!await db.schema.hasTable('daily_summaries')) {
    await db.schema.createTable('daily_summaries', t => {
      t.increments('id');
      t.string('person_id').references('person_id').inTable('employees');
      t.date('work_date').notNullable();
      t.integer('shift_id').references('id').inTable('shifts');
      t.timestamp('first_in');
      t.timestamp('last_out');
      t.string('expected_start');
      t.string('expected_end');
      t.integer('regular_minutes').defaultTo(0);
      t.integer('overtime_25_minutes').defaultTo(0);
      t.integer('overtime_50_minutes').defaultTo(0);
      t.integer('overtime_100_minutes').defaultTo(0);
      t.integer('night_minutes').defaultTo(0);
      t.integer('late_minutes').defaultTo(0);
      t.integer('early_out_minutes').defaultTo(0);
      t.string('status');  // 'present'|'absent'|'incomplete'|'holiday'|'weekend'
      t.boolean('is_holiday').defaultTo(false);
      t.boolean('is_weekend').defaultTo(false);
      t.text('notes');
      t.timestamps(true, true);
      t.unique(['person_id', 'work_date']);
    });
  }

  // ── Anomalies ──────────────────────────────────────────────
  if (!await db.schema.hasTable('anomalies')) {
    await db.schema.createTable('anomalies', t => {
      t.increments('id');
      t.string('person_id').references('person_id').inTable('employees');
      t.string('person_name');
      t.date('work_date').notNullable();
      t.string('type').notNullable();
      // 'missing_checkout'|'missing_checkin'|'late'|'early_out'|'absent'|'short_break'
      t.text('description');
      t.string('severity').defaultTo('warning');  // 'info'|'warning'|'error'
      t.boolean('resolved').defaultTo(false);
      t.string('resolved_by');
      t.timestamp('resolved_at');
      t.text('resolution_note');
      t.timestamps(true, true);
    });
  }

  // ── Badges manuels (corrections RH) ───────────────────────
  if (!await db.schema.hasTable('manual_badges')) {
    await db.schema.createTable('manual_badges', t => {
      t.increments('id');
      t.string('person_id').references('person_id').inTable('employees');
      t.string('person_name');
      t.timestamp('swipe_time').notNullable();
      t.string('event_type').notNullable();  // '1'=entrée '2'=sortie
      t.text('reason');
      t.string('created_by').defaultTo('RH');
      t.timestamps(true, true);
    });
  }

  // ── Config export Sage X3 ──────────────────────────────────
  if (!await db.schema.hasTable('sage_export_config')) {
    await db.schema.createTable('sage_export_config', t => {
      t.increments('id');
      t.string('hour_type').unique().notNullable();
      t.string('rubrique_code').notNullable();
      t.string('rubrique_label');
      t.boolean('enabled').defaultTo(true);
      t.integer('sort_order').defaultTo(0);
    });
    await db('sage_export_config').insert([
      { hour_type: 'regular',       rubrique_code: 'HEURE_NORM',    rubrique_label: 'Heures normales',         enabled: true,  sort_order: 1 },
      { hour_type: 'overtime_25',   rubrique_code: 'HEURE_SUP_25',  rubrique_label: 'Heures sup 25%',          enabled: true,  sort_order: 2 },
      { hour_type: 'overtime_50',   rubrique_code: 'HEURE_SUP_50',  rubrique_label: 'Heures sup 50%',          enabled: true,  sort_order: 3 },
      { hour_type: 'overtime_100',  rubrique_code: 'HEURE_SUP_100', rubrique_label: 'Heures sup 100% (dim/fér)', enabled: true, sort_order: 4 },
      { hour_type: 'night',         rubrique_code: 'HEURE_NUIT',    rubrique_label: 'Heures de nuit',          enabled: true,  sort_order: 5 },
      { hour_type: 'holiday',       rubrique_code: 'HEURE_FERIE',   rubrique_label: 'Heures jours fériés',     enabled: true,  sort_order: 6 },
      { hour_type: 'absence',       rubrique_code: 'ABSENCE',       rubrique_label: 'Absences',                enabled: false, sort_order: 7 },
    ]);
  }

  logger.info('DB migrations OK');
}

module.exports = { db, migrate };
