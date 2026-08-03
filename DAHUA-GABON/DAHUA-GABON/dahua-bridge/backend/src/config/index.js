const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });

module.exports = {
  server: {
    port: process.env.PORT || 3001,
    host: process.env.HOST || 'localhost',
  },

  dss: {
    ip:         process.env.DSS_IP         || '192.168.1.147',
    port:       process.env.DSS_PORT       || 443,
    accessKey:  process.env.DSS_ACCESS_KEY || 'TNJKFKEN7JDKKZDVMBMNNNSS',
    secretKey:  process.env.DSS_SECRET_KEY || 'DDAQYKYT2FKLNGJLUBQDB6ZW76QTR7LR',
    username:   process.env.DSS_USERNAME   || 'system',
    password:   process.env.DSS_PASSWORD   || 'Doukiflorence@12345',
    // Intervalle de sync complet en minutes (depts + employés + historique)
    syncInterval: parseInt(process.env.DSS_SYNC_INTERVAL || '5', 10),
    // Intervalle de sync temps réel en secondes (badges récents uniquement)
    realtimeInterval: parseInt(process.env.DSS_REALTIME_INTERVAL || '30', 10),
  },

  db: {
    host:     process.env.DB_HOST     || 'localhost',
    port:     parseInt(process.env.DB_PORT || '5432', 10),
    database: process.env.DB_NAME     || 'dahua_bridge',
    user:     process.env.DB_USER     || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
  },

  sage: {
    // Dossier de dépôt des exports CSV pour Sage X3
    exportPath: process.env.SAGE_EXPORT_PATH || 'C:\\SageExports',
    // Format de la date dans les exports
    dateFormat: process.env.SAGE_DATE_FORMAT || 'DD/MM/YYYY',
  },
};
