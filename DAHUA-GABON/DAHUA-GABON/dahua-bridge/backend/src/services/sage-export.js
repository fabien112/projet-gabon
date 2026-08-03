/**
 * Export des pointages vers Sage X3
 *
 * Génère un fichier CSV compatible import Sage X3
 * Format : Matricule ; Date ; Heure ; Type (E=Entrée / S=Sortie)
 */

const fs     = require('fs');
const path   = require('path');
const dayjs  = require('dayjs');
const { createObjectCsvWriter } = require('csv-writer');
const config = require('../config');
const logger = require('./logger');

const EVENT_MAP = {
  '1': 'E',   // Entrée
  '2': 'S',   // Sortie
  '3': 'S',   // Sortie externe
  '4': 'E',   // Retour = Entrée
};

/**
 * Génère le CSV et retourne le chemin du fichier créé.
 * @param {Array}  records  - Pointages (depuis DB ou DSS direct)
 * @param {string} filename - Nom du fichier (sans extension)
 */
async function exportToCsv(records, filename) {
  if (!fs.existsSync(config.sage.exportPath)) {
    fs.mkdirSync(config.sage.exportPath, { recursive: true });
  }

  const filePath = path.join(config.sage.exportPath, `${filename}.csv`);

  const writer = createObjectCsvWriter({
    path: filePath,
    fieldDelimiter: ';',
    header: [
      { id: 'matricule',  title: 'MATRICULE'  },
      { id: 'nom',        title: 'NOM'        },
      { id: 'date',       title: 'DATE'       },
      { id: 'heure',      title: 'HEURE'      },
      { id: 'type',       title: 'TYPE'       },
      { id: 'departement',title: 'DEPARTEMENT'},
    ],
  });

  const rows = records.map(r => ({
    matricule:   r.personId   || r.person_id   || '',
    nom:         r.personName || r.person_name || '',
    date:        dayjs(r.swipeTime || r.swipe_time).format(config.sage.dateFormat),
    heure:       dayjs(r.swipeTime || r.swipe_time).format('HH:mm:ss'),
    type:        EVENT_MAP[r.eventType || r.event_type] || 'E',
    departement: r.deptName   || r.dept_name   || '',
  }));

  await writer.writeRecords(rows);
  logger.info(`Sage export: ${rows.length} lignes → ${filePath}`);
  return filePath;
}

/**
 * Export mensuel : nom de fichier automatique POINTAGES_AAAA_MM.csv
 */
async function exportMonth(records, year, month) {
  const filename = `POINTAGES_${year}_${String(month).padStart(2, '0')}`;
  return exportToCsv(records, filename);
}

module.exports = { exportToCsv, exportMonth };
