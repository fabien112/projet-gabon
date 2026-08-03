# People Counting — déploiement

Deux modes : **JAR (recommandé si pas de virtualisation)** ou **Docker**.

---

## Mode JAR (ce client)

Prérequis chez le client : **Java 21** uniquement.

Pack prêt : dossier `dist-client/`

```
dist-client/
  dss-integration.jar
  web/                 # frontend buildé
  .env.example
  start.bat
  stop.bat
  data/                # H2 créé au 1er lancement
```

1. Copier `dist-client` chez le client
2. `.env.example` → `.env` (DSS_HOST, user, mdp)
3. Double-clic `start.bat`
4. Ouvrir http://localhost:8080/

Rebuild chez toi :
```powershell
cd dss-frontend ; npm run build
cd ..\dss-integration ; .\mvnw.cmd -DskipTests package
# puis recopier jar + dist dans dist-client
```

---

## Mode Docker (optionnel)

Prérequis : Docker Desktop / Engine + virtualisation activée.

1. Copier `.env.example` → `.env`
2. Renseigner `DSS_HOST`, `DSS_USERNAME`, `DSS_PASSWORD`
3. `docker compose up -d --build`
4. http://IP-DU-SERVEUR/ (port `WEB_PORT`, défaut 80)
