/**
 * Script de build complet :
 *   1. Build Vue.js → frontend/dist/
 *   2. Copie dist/ dans backend/public/
 *   3. Compile Node.js → dist/bridge-server.exe  (via pkg)
 *   4. Copie .env dans dist/
 *
 * Usage : node build.js
 */

const { execSync } = require('child_process');
const fs   = require('fs');
const path = require('path');

const ROOT     = __dirname;
const DIST     = path.join(ROOT, 'dist');
const FRONTEND = path.join(ROOT, 'frontend');
const BACKEND  = path.join(ROOT, 'backend');

function run(cmd, cwd) {
  console.log(`\n> ${cmd}`);
  execSync(cmd, { cwd: cwd || ROOT, stdio: 'inherit' });
}

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, entry.name);
    const d = path.join(dest, entry.name);
    entry.isDirectory() ? copyDir(s, d) : fs.copyFileSync(s, d);
  }
}

// ── 1. Build frontend ────────────────────────────────────────
console.log('\n=== BUILD FRONTEND (Vue.js) ===');
run('npm run build', FRONTEND);

// ── 2. Copier dist → backend pour être embarqué ──────────────
console.log('\n=== COPIE dist → backend/public ===');
const frontendDist = path.join(FRONTEND, 'dist');
const backendPublic = path.join(BACKEND, '..', 'frontend', 'dist');
// Le serveur cherche déjà dans ../frontend/dist, pas besoin de copier

// ── 3. Créer le dossier dist/ ────────────────────────────────
fs.mkdirSync(DIST, { recursive: true });

// ── 4. Compiler le backend en .exe ───────────────────────────
console.log('\n=== COMPILATION backend → .exe (pkg) ===');
run(
  `npx pkg src/index.js --targets node20-win-x64 --output ${path.join(DIST, 'bridge-server.exe')} --compress GZip`,
  BACKEND
);

// ── 5. Copier les fichiers nécessaires dans dist/ ────────────
console.log('\n=== COPIE des ressources dans dist/ ===');
fs.copyFileSync(path.join(BACKEND, '.env'), path.join(DIST, '.env'));
copyDir(frontendDist, path.join(DIST, 'frontend', 'dist'));

// ── 6. Créer le script de démarrage ──────────────────────────
fs.writeFileSync(path.join(DIST, 'start.bat'), [
  '@echo off',
  'echo Démarrage Dahua Bridge...',
  'bridge-server.exe',
  'pause',
].join('\r\n'));

console.log('\n✅ Build terminé !');
console.log(`   Exécutable : ${path.join(DIST, 'bridge-server.exe')}`);
console.log(`   Lancer     : ${path.join(DIST, 'start.bat')}`);
console.log('\n   Pour créer l\'installeur : compiler installer/setup.iss avec Inno Setup');
