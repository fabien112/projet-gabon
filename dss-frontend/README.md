# dss-frontend

Interface **People Counting – Rapport personnalisé**, séparée du backend Spring Boot.

## Prérequis

- Backend `dss-integration` démarré sur `http://localhost:8080`
- Node.js 18+

## Lancer

```bash
cd dss-frontend
npm install
npm run dev
```

Ouvrir : http://localhost:5173

Le proxy Vite redirige `/api/*` vers le backend `8080`.

## Fonctionnalités

- Filtres : période, tranche horaire, caméra, groupement jour
- KPI : entrées, sorties, présence nette
- Table détail journalier
- Graphiques évolution + répartition jours de semaine
