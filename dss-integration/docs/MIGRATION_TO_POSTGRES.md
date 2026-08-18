# Migration H2 → PostgreSQL

Guide pas-à-pas pour le **passage en production Docker** après synchronisation locale (JAR + H2).

---

## Quand migrer ?

1. Sync DSS **terminée** en local (données à jour dans H2)
2. Vérification rapport / page Sync OK
3. **Arrêt du JAR** (`stop.bat`) — libère le fichier H2

---

## Script automatique (recommandé)

### Windows

```powershell
cd DAHUA
copy .env.example .env
# Renseigner POSTGRES_PASSWORD, PGADMIN_PASSWORD, DSS_*

# Simulation (aucune écriture)
.\scripts\migrate-h2-to-postgres.ps1 -DryRun

# Migration réelle
.\scripts\migrate-h2-to-postgres.ps1
```

### Linux

```bash
chmod +x scripts/migrate-h2-to-postgres.sh
./scripts/migrate-h2-to-postgres.sh
```

### Options PowerShell

| Option | Description |
|--------|-------------|
| `-DryRun` | Compte les lignes sans écrire |
| `-H2BasePath` | Chemin base H2 (sans `.mv.db`) |
| `-EnvFile` | Fichier `.env` à utiliser |
| `-SkipDockerDb` | Postgres déjà démarré |
| `-NoComposeUp` | Ne pas lancer `docker compose up` à la fin |

---

## Ce que fait le script

```
[Prérequis]  JAR arrêté, sync OK
     ↓
[1/3]        docker compose up -d db  (PostgreSQL + volume)
     ↓
[2/3]        H2ToPostgresMigrator (Java)
             — crée le schéma V1 si absent
             — vide les tables PG
             — copie camera, app_user, people_counting_hourly, sync_meta
             — réinitialise les séquences
     ↓
[3/3]        Vérification SQL + docker compose up -d --build
```

---

## Outil Java manuel

```powershell
cd dss-integration
.\mvnw.cmd -q compile exec:java `
  "-Dexec.mainClass=com.company.dss.tools.H2ToPostgresMigrator" `
  "-Dexec.classpathScope=runtime" `
  "-Dexec.args=--h2-path `"..\dist-client\data\dss`" --pg-url jdbc:postgresql://localhost:5432/dss --pg-user dss_user --pg-password VOTRE_MDP"
```

Options : `--dry-run`, `--help`

---

## Vérifications post-migration

```sql
SELECT COUNT(*), MIN(slot_date), MAX(slot_date) FROM people_counting_hourly;
SELECT COUNT(*) FROM camera;
SELECT * FROM sync_meta;
```

```powershell
docker compose logs -f api
curl http://localhost/api/reports/status
```

---

## Dépannage

| Problème | Solution |
|----------|----------|
| `Base H2 introuvable` | `-H2BasePath` vers le dossier contenant `dss.mv.db` |
| Connexion PG refusée | Vérifier `docker compose ps db` et port `127.0.0.1:5432` |
| Fichier H2 verrouillé | Arrêter le JAR / toute instance Java DataExpert |
| Counts différents H2/PG | Relancer sans `-DryRun` ; vérifier logs migrator |

---

## Archive

Conserver une copie du H2 source avant migration :

```powershell
Copy-Item dist-client\data\dss.mv.db backup-h2-20260818.mv.db
```

---

## Alternative : resync depuis le DSS

Si la migration échoue, vous pouvez démarrer Docker avec une base vide et relancer une sync complète depuis l'UI (historique DSS depuis le 16/02/2026).
