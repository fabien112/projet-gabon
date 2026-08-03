const express = require('express');
const cors    = require('cors');
const path    = require('path');
const config  = require('./config');
const logger  = require('./services/logger');
const { migrate } = require('./db');
const { startScheduler } = require('./jobs/sync');

const eventBus = require('./services/event-bus');

const app = express();

app.use(cors({ origin: '*' }));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// ── API Routes ────────────────────────────────────────────────
app.use('/api/attendance', require('./routes/attendance'));
app.use('/api/employees',  require('./routes/employees'));
app.use('/api/export',     require('./routes/export'));
app.use('/api/config',     require('./routes/config'));
app.use('/api/anomalies',  require('./routes/anomalies'));
app.use('/api/summaries',  require('./routes/summaries'));

// ── Statut du serveur ─────────────────────────────────────────
app.get('/api/status', (req, res) => {
  res.json({
    status:  'running',
    version: '1.0.0',
    dssIp:   config.dss.ip,
    time:    new Date().toISOString(),
  });
});

// ── Server-Sent Events — flux temps réel des badges ──────────
app.get('/api/events', (req, res) => {
  res.set({
    'Content-Type':  'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection':    'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  res.flushHeaders();

  // Heartbeat toutes les 20s pour garder la connexion ouverte
  const heartbeat = setInterval(() => {
    res.write(': ping\n\n');
  }, 20000);

  const onBadge = (badges) => {
    res.write(`event: badge\ndata: ${JSON.stringify(badges)}\n\n`);
  };

  eventBus.on('new_badges', onBadge);

  req.on('close', () => {
    clearInterval(heartbeat);
    eventBus.off('new_badges', onBadge);
  });
});

// ── Frontend Vue (production) — servi depuis dist/ ────────────
const frontendDist = path.join(__dirname, '..', '..', 'frontend', 'dist');
if (require('fs').existsSync(frontendDist)) {
  app.use(express.static(frontendDist));
  app.get('*', (req, res) => {
    res.sendFile(path.join(frontendDist, 'index.html'));
  });
}

// ── Démarrage ─────────────────────────────────────────────────
async function start() {
  try {
    await migrate();
    logger.info('Base de données initialisée');

    app.listen(config.server.port, config.server.host, () => {
      logger.info(`Serveur démarré sur http://${config.server.host}:${config.server.port}`);
    });

    startScheduler();
  } catch (err) {
    logger.error(`Erreur démarrage: ${err.message}`);
    process.exit(1);
  }
}

start();
