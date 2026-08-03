# Dahua Bridge — DSS Pro ↔ Sage X3

Bridge middleware entre Dahua DSS Pro V8.7 et Sage X3.

## Structure

```
dahua-bridge/
├── backend/          Node.js + Express (API REST + sync DSS)
│   ├── src/
│   │   ├── config/   Configuration (.env)
│   │   ├── services/ Auth DSS, Attendance, Export Sage
│   │   ├── routes/   /api/attendance, /api/employees, /api/export
│   │   ├── db/       PostgreSQL + migrations
│   │   └── jobs/     Cron sync toutes les 5 min
│   └── .env          Variables d'environnement
├── frontend/         Vue.js 3 + Tailwind CSS
│   └── src/views/    Dashboard, Pointages, Employés, Export
├── installer/        setup.iss (Inno Setup)
├── build.js          Script de compilation
└── dist/             Livrables compilés
```

## Développement

```bash
# Installer les dépendances
npm run install:all

# Démarrer en mode dev (deux terminaux)
npm run dev:backend   # API sur http://localhost:3001
npm run dev:frontend  # UI sur http://localhost:3000
```

## Build (production)

```bash
# Compiler backend (.exe) + frontend (dist) + package
node build.js

# Puis ouvrir installer/setup.iss dans Inno Setup
# → Génère dist/DahuaBridge-Setup-v1.0.exe
```

## Configuration

Éditer `backend/.env` avant le build :
- `DSS_IP` : IP du serveur DSS Pro
- `DSS_ACCESS_KEY` / `DSS_SECRET_KEY` : credentials Bridge
- `DSS_USERNAME` / `DSS_PASSWORD` : compte DSS
- `DB_*` : connexion PostgreSQL
- `SAGE_EXPORT_PATH` : dossier d'export CSV

## APIs exposées

| Endpoint | Description |
|---|---|
| `GET /api/attendance` | Liste des pointages (filtres date/dept/type) |
| `GET /api/attendance/stats` | Stats du jour |
| `GET /api/attendance/daily-summary` | Résumé journalier |
| `GET /api/employees` | Liste des employés |
| `GET /api/employees/departments` | Liste des départements |
| `POST /api/export/sage` | Export CSV Sage X3 |
| `POST /api/export/sync` | Sync manuelle DSS |
| `GET /api/export/sync-log` | Historique syncs |
| `GET /api/status` | Statut du serveur |
