People Counting — pack client (JAR, mode alternatif)

Ce pack reste disponible en parallèle du déploiement Docker.
Pour la production, préférer Docker Compose (voir DEPLOY.md à la racine du repo).

Prérequis : Java 21 (https://adoptium.net/)

1. Copier .env.example → .env
2. Renseigner DSS_HOST, DSS_USERNAME, DSS_PASSWORD
3. Double-clic start.bat  (ou : java -jar dss-integration.jar)
4. Ouvrir http://localhost:8080/

Contenu :
- dss-integration.jar  = backend + API
- web\                 = frontend buildé
- data\                = base H2 (créée au 1er lancement)
