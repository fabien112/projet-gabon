<template>
  <div class="page lea-page">
    <header class="topbar">
      <div class="title-wrap">
        <span class="logo" aria-hidden="true">🔄</span>
        <h1>Synchronisation historique</h1>
      </div>
      <div class="top-actions">
        <RouterLink class="btn-outline" to="/">← Rapport</RouterLink>
        <button type="button" class="btn-outline" @click="doLogout">Déconnexion</button>
      </div>
    </header>

    <section class="panel info-panel">
      <h2>État</h2>
      <div class="meta-grid">
        <div>
          <span class="label">Dernière sync terminée</span>
          <strong>{{ formatDateTime(status.lastJobFinishedAt) }}</strong>
        </div>
        <div>
          <span class="label">Statut dernier job</span>
          <strong :class="statusClass(status.lastJobStatus)">{{ status.lastJobStatus || '—' }}</strong>
        </div>
        <div>
          <span class="label">Dernière période sync</span>
          <strong>
            <template v-if="status.lastJobFrom && status.lastJobTo">
              {{ formatDate(status.lastJobFrom) }} → {{ formatDate(status.lastJobTo) }}
            </template>
            <template v-else>—</template>
          </strong>
        </div>
        <div>
          <span class="label">Dernier jour couvert en base</span>
          <strong>{{ status.lastCoveredDateInDb ? formatDate(status.lastCoveredDateInDb) : '—' }}</strong>
        </div>
      </div>
      <p v-if="status.lastJobMessage" class="last-msg">{{ status.lastJobMessage }}</p>
      <p class="hint">
        La sync ne démarre <strong>jamais</strong> toute seule. Tu choisis la période et tu lances.
        Relancer la même période met à jour / complète (pas de doublons).
      </p>
    </section>

    <section class="panel form-panel">
      <h2>Lancer une synchronisation</h2>
      <div class="form-row">
        <label>
          <span class="label">Du</span>
          <input v-model="from" type="date" :max="to" :disabled="status.running" />
        </label>
        <label>
          <span class="label">Au</span>
          <input v-model="to" type="date" :min="from" :max="today" :disabled="status.running" />
        </label>
        <button
          class="generate"
          :disabled="status.running || !canStart"
          @click="startSync"
        >
          <span v-if="status.running" class="spinner" aria-hidden="true"></span>
          {{ status.running ? 'Synchronisation…' : 'Lancer la synchronisation' }}
        </button>
      </div>
      <p v-if="formError" class="error">{{ formError }}</p>
      <p v-if="apiError" class="error">{{ apiError }}</p>
    </section>

    <section v-if="status.running || status.phase === 'SUCCESS' || status.phase === 'FAILED'" class="panel progress-panel">
      <h2>Progression</h2>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progressPct + '%' }"></div>
      </div>
      <p>
        {{ status.daysDone }} / {{ status.daysTotal }} jour(s)
        <template v-if="status.currentDate"> — jour courant : {{ formatDate(status.currentDate) }}</template>
      </p>
      <p>{{ status.message }}</p>
      <p>Lignes upsertées : {{ status.rowsUpserted }}</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { logoutApp } from '../api/auth'
import { fetchSyncStatus, startHistorySync } from '../api/sync'

const today = new Date().toISOString().slice(0, 10)
const router = useRouter()
const monthAgo = new Date()
monthAgo.setDate(monthAgo.getDate() - 30)

const from = ref(monthAgo.toISOString().slice(0, 10))
const to = ref(today)
const formError = ref('')
const apiError = ref('')

const status = reactive({
  running: false,
  phase: 'IDLE',
  fromDate: null,
  toDate: null,
  currentDate: null,
  daysTotal: 0,
  daysDone: 0,
  rowsUpserted: 0,
  message: '',
  lastJobFinishedAt: null,
  lastCoveredDateInDb: null,
  lastJobStatus: null,
  lastJobMessage: null,
  lastJobFrom: null,
  lastJobTo: null,
})

let timer = null

const canStart = computed(() => from.value && to.value && to.value >= from.value)

const progressPct = computed(() => {
  if (!status.daysTotal) return 0
  return Math.min(100, Math.round((status.daysDone / status.daysTotal) * 100))
})

function applyStatus(data) {
  Object.assign(status, data)
}

async function doLogout() {
  try {
    await logoutApp()
  } catch {
    // ignore
  }
  router.replace('/login')
}

function formatDate(isoDate) {
  if (!isoDate) return '—'
  const [y, m, d] = String(isoDate).slice(0, 10).split('-')
  return `${d}/${m}/${y}`
}

function formatDateTime(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('fr-FR')
  } catch {
    return String(iso)
  }
}

function statusClass(s) {
  if (s === 'SUCCESS') return 'ok'
  if (s === 'FAILED' || s === 'RUNNING') return s === 'FAILED' ? 'ko' : 'run'
  return ''
}

async function refresh() {
  try {
    applyStatus(await fetchSyncStatus())
    apiError.value = ''
  } catch (e) {
    apiError.value = e?.response?.data?.message || e.message || 'Impossible de lire le statut sync'
  }
}

async function startSync() {
  formError.value = ''
  apiError.value = ''
  if (!canStart.value) {
    formError.value = 'Période invalide'
    return
  }
  try {
    applyStatus(await startHistorySync({ from: from.value, to: to.value }))
  } catch (e) {
    apiError.value = e?.response?.data?.message || e.message || 'Échec démarrage sync'
  }
}

onMounted(async () => {
  await refresh()
  timer = setInterval(refresh, 2000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.page {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px 20px 40px;
}
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}
.title-wrap h1 {
  font-size: 1.35rem;
  margin: 0;
}
.btn-outline {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 10px;
  padding: 8px 12px;
  text-decoration: none;
  color: #0f172a;
  font-weight: 600;
  font-size: 0.85rem;
  cursor: pointer;
}
.panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 18px 20px;
  margin-bottom: 16px;
}
.panel h2 {
  margin: 0 0 12px;
  font-size: 1.05rem;
}
.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.label {
  display: block;
  font-size: 0.78rem;
  color: #64748b;
  margin-bottom: 4px;
}
.ok { color: #0d9488; }
.ko { color: #dc2626; }
.run { color: #d97706; }
.last-msg, .hint {
  margin-top: 12px;
  color: #475569;
  font-size: 0.92rem;
}
.form-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: end;
}
.form-row label {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.form-row input {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 8px 10px;
}
.generate {
  border: none;
  background: #0d9488;
  color: #fff;
  border-radius: 10px;
  padding: 10px 16px;
  font-weight: 700;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.generate:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.error {
  color: #dc2626;
  margin-top: 10px;
}
.progress-bar {
  height: 10px;
  background: #e2e8f0;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 10px;
}
.progress-fill {
  height: 100%;
  background: #0d9488;
  transition: width 0.3s ease;
}
.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
@media (max-width: 700px) {
  .meta-grid { grid-template-columns: 1fr; }
}
</style>
