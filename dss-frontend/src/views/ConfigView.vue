<template>
  <div class="page">
    <header class="topbar">
      <div class="title-wrap">
        <span class="logo" aria-hidden="true">⚙️</span>
        <h1>Configuration</h1>
      </div>
      <div class="top-actions">
        <RouterLink class="btn-outline" to="/">← Rapport</RouterLink>
        <button type="button" class="btn-outline" @click="doLogout">Déconnexion</button>
      </div>
    </header>

    <!-- Session DSS : bandeau dédié -->
    <section class="session-bar" :class="status.dssSessionActive ? 'on' : 'off'">
      <div class="session-left">
        <span class="session-dot" aria-hidden="true" />
        <div>
          <p class="session-title">Session DSS</p>
          <p class="session-state">
            {{ status.dssSessionActive ? 'Connectée et prête' : 'Inactive — reconnexion nécessaire' }}
          </p>
        </div>
      </div>
      <button
        type="button"
        class="btn-reconnect"
        :disabled="reconnecting || status.dssSessionActive"
        @click="doReconnect"
      >
        {{ reconnecting ? 'Reconnexion…' : status.dssSessionActive ? 'Connecté' : 'Reconnecter' }}
      </button>
    </section>

    <nav class="tabs" aria-label="Sections de configuration">
      <button
        type="button"
        class="tab"
        :class="{ active: tab === 'sync' }"
        @click="setTab('sync')"
      >
        Synchronisation
      </button>
      <button
        type="button"
        class="tab"
        :class="{ active: tab === 'cameras' }"
        @click="setTab('cameras')"
      >
        Caméras
      </button>
    </nav>

    <div v-show="tab === 'cameras'">
      <CameraConfigPanel :dss-ready="status.dssSessionActive" />
    </div>

    <div v-show="tab === 'sync'" class="layout">
      <!-- Action principale -->
      <section class="panel action-panel">
        <h2>Lancer une synchronisation</h2>
        <p class="lead">
          Choisissez la période à récupérer depuis DSS. Relancer la même période met à jour
          les données sans créer de doublons.
        </p>

        <div class="form-grid">
          <label class="field">
            <span class="label">Du</span>
            <input v-model="from" type="date" :max="to" :disabled="status.running" />
          </label>
          <label class="field">
            <span class="label">Au</span>
            <input v-model="to" type="date" :min="from" :max="today" :disabled="status.running" />
          </label>
        </div>

        <button
          class="generate"
          :disabled="status.running || !canStart"
          @click="startSync"
        >
          <span v-if="status.running" class="spinner" aria-hidden="true"></span>
          {{ status.running ? 'Synchronisation en cours…' : 'Lancer la synchronisation' }}
        </button>

        <p v-if="formError" class="error">{{ formError }}</p>
        <p v-if="apiError" class="error">{{ apiError }}</p>

        <!-- Progression intégrée sous l'action -->
        <div
          v-if="status.running || status.phase === 'SUCCESS' || status.phase === 'FAILED'"
          class="progress-block"
        >
          <div class="progress-head">
            <h3>Progression</h3>
            <span class="badge" :class="statusClass(status.phase || status.lastJobStatus)">
              {{ status.phase || status.lastJobStatus || '—' }}
            </span>
          </div>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progressPct + '%' }"></div>
          </div>
          <div class="progress-meta">
            <span>{{ status.daysDone }} / {{ status.daysTotal }} jour(s)</span>
            <span v-if="status.currentDate">Jour : {{ formatDate(status.currentDate) }}</span>
            <span>{{ status.rowsUpserted }} ligne(s)</span>
          </div>
          <p v-if="status.message" class="progress-msg">{{ status.message }}</p>
        </div>
      </section>

      <!-- Infos secondaires -->
      <aside class="side-stack">
        <section class="panel side-panel poll-panel" :class="pollPanelClass">
          <div class="poll-head">
            <h2>Poll automatique</h2>
            <span class="poll-live" :class="{ on: status.pollInProgress || pollRecent }">
              <span class="poll-pulse" aria-hidden="true" />
              {{ pollLiveLabel }}
            </span>
          </div>
          <p class="poll-lead">
            Récupération automatique du jour en cours depuis DSS vers la base locale.
          </p>
          <dl class="facts">
            <div>
              <dt>État</dt>
              <dd :class="pollStatusClass">{{ pollStateLabel }}</dd>
            </div>
            <div>
              <dt>Dernier poll</dt>
              <dd>{{ formatDateTime(status.pollLastAt) }}</dd>
            </div>
            <div>
              <dt>Résultat</dt>
              <dd>{{ status.pollLastMessage || '—' }}</dd>
            </div>
            <div>
              <dt>Lignes / créneaux</dt>
              <dd>
                {{ status.pollLastRowsUpserted ?? 0 }} ligne(s)
                <template v-if="status.pollLastActiveSlots != null">
                  · {{ status.pollLastActiveSlots }} actif(s)
                </template>
              </dd>
            </div>
          </dl>
        </section>

        <section class="panel side-panel">
        <h2>Dernière synchronisation</h2>
        <dl class="facts">
          <div>
            <dt>Terminée le</dt>
            <dd>{{ formatDateTime(status.lastJobFinishedAt) }}</dd>
          </div>
          <div>
            <dt>Statut</dt>
            <dd :class="statusClass(status.lastJobStatus)">{{ status.lastJobStatus || '—' }}</dd>
          </div>
          <div>
            <dt>Période</dt>
            <dd>
              <template v-if="status.lastJobFrom && status.lastJobTo">
                {{ formatDate(status.lastJobFrom) }} → {{ formatDate(status.lastJobTo) }}
              </template>
              <template v-else>—</template>
            </dd>
          </div>
          <div>
            <dt>Dernier jour en base</dt>
            <dd>{{ status.lastCoveredDateInDb ? formatDate(status.lastCoveredDateInDb) : '—' }}</dd>
          </div>
        </dl>
        <p v-if="status.lastJobMessage" class="side-msg">{{ status.lastJobMessage }}</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { logoutApp } from '../api/auth'
import { fetchSyncStatus, reconnectDss, startHistorySync } from '../api/sync'
import CameraConfigPanel from '../components/CameraConfigPanel.vue'

const today = new Date().toISOString().slice(0, 10)
const router = useRouter()
const route = useRoute()
const monthAgo = new Date()
monthAgo.setDate(monthAgo.getDate() - 30)

const tab = ref(route.query.tab === 'cameras' ? 'cameras' : 'sync')

function setTab(next) {
  tab.value = next
  const query = { ...route.query }
  if (next === 'sync') {
    delete query.tab
  } else {
    query.tab = next
  }
  router.replace({ query })
}

const from = ref(monthAgo.toISOString().slice(0, 10))
const to = ref(today)
const formError = ref('')
const apiError = ref('')
const reconnecting = ref(false)

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
  dssSessionActive: false,
  pollEnabled: true,
  pollInProgress: false,
  pollSuspended: false,
  pollInterval: '45s',
  pollLastAt: null,
  pollLastStatus: null,
  pollLastMessage: null,
  pollLastRowsUpserted: 0,
  pollLastActiveSlots: 0,
  pollLastDate: null,
})

let timer = null
const nowTick = ref(Date.now())
let tickTimer = null

const canStart = computed(() => from.value && to.value && to.value >= from.value)

const progressPct = computed(() => {
  if (!status.daysTotal) return 0
  return Math.min(100, Math.round((status.daysDone / status.daysTotal) * 100))
})

const pollRecent = computed(() => {
  if (!status.pollLastAt) return false
  const age = nowTick.value - new Date(status.pollLastAt).getTime()
  return age >= 0 && age < 12_000
})

const pollLiveLabel = computed(() => {
  if (status.pollInProgress) return 'En cours…'
  if (status.pollSuspended) return 'Suspendu'
  if (!status.pollEnabled) return 'Désactivé'
  if (pollRecent.value) return 'Signalé'
  return 'En attente'
})

const pollStateLabel = computed(() => {
  if (status.pollInProgress) return 'Pull en cours'
  if (status.pollSuspended) return 'Suspendu (sync historique)'
  if (!status.pollEnabled) return 'Désactivé'
  if (status.pollLastStatus === 'OK') return 'OK'
  if (status.pollLastStatus === 'FAILED') return 'Échec'
  if (status.pollLastStatus === 'SKIPPED') return 'Ignoré'
  return status.pollLastStatus || 'En attente'
})

const pollStatusClass = computed(() => {
  if (status.pollInProgress) return 'run'
  if (status.pollLastStatus === 'OK') return 'ok'
  if (status.pollLastStatus === 'FAILED') return 'ko'
  if (status.pollSuspended || status.pollLastStatus === 'SKIPPED') return 'run'
  return ''
})

const pollPanelClass = computed(() => {
  if (status.pollInProgress || pollRecent.value) return 'live'
  if (status.pollLastStatus === 'FAILED') return 'fail'
  if (!status.pollEnabled || status.pollSuspended) return 'muted'
  return ''
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
  if (s === 'FAILED') return 'ko'
  if (s === 'RUNNING') return 'run'
  return ''
}

async function refresh() {
  try {
    applyStatus(await fetchSyncStatus())
    // Ne pas effacer une erreur de sync récente si refresh périodique
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

async function doReconnect() {
  reconnecting.value = true
  apiError.value = ''
  try {
    const res = await reconnectDss()
    await refresh()
    if (!res?.dssSessionActive) {
      apiError.value = res?.message || 'Session DSS toujours inactive'
    }
  } catch (e) {
    apiError.value = e?.response?.data?.message || e.message || 'Échec reconnexion DSS'
    await refresh()
  } finally {
    reconnecting.value = false
  }
}

onMounted(async () => {
  await refresh()
  timer = setInterval(refresh, 2000)
  tickTimer = setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (tickTimer) clearInterval(tickTimer)
})
</script>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px 20px 48px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--blue-soft, #dbeafe);
  display: grid;
  place-items: center;
  font-size: 1rem;
}

h1 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--ink, #0f172a);
}

.top-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 16px;
  padding: 4px;
  background: #fff;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 12px;
  width: fit-content;
}

.tab {
  border: 0;
  background: transparent;
  border-radius: 9px;
  padding: 8px 16px;
  font-weight: 700;
  font-size: 0.88rem;
  color: var(--muted, #64748b);
  cursor: pointer;
}

.tab.active {
  background: #0f172a;
  color: #fff;
}

.btn-outline {
  border: 1px solid var(--line, #e2e8f0);
  background: #fff;
  border-radius: 10px;
  padding: 8px 12px;
  text-decoration: none;
  color: inherit;
  font-weight: 600;
  font-size: 0.85rem;
  cursor: pointer;
}

/* Session DSS */
.session-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-radius: 14px;
  border: 1px solid var(--line, #e2e8f0);
  margin-bottom: 16px;
}

.session-bar.on {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.session-bar.off {
  background: #fef2f2;
  border-color: #fecaca;
}

.session-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.session-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}

.session-bar.on .session-dot {
  background: #0d9488;
  box-shadow: 0 0 0 4px rgba(13, 148, 136, 0.2);
}

.session-bar.off .session-dot {
  background: #dc2626;
  box-shadow: 0 0 0 4px rgba(220, 38, 38, 0.15);
}

.session-title {
  margin: 0;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--muted, #64748b);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.session-state {
  margin: 2px 0 0;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--ink, #0f172a);
}

.btn-reconnect {
  border: 0;
  border-radius: 10px;
  padding: 10px 14px;
  font-weight: 700;
  font-size: 0.85rem;
  cursor: pointer;
  white-space: nowrap;
  background: #0f172a;
  color: #fff;
}

.session-bar.on .btn-reconnect {
  background: #0d9488;
  cursor: default;
  opacity: 0.85;
}

.btn-reconnect:disabled {
  opacity: 0.7;
  cursor: wait;
}

.session-bar.on .btn-reconnect:disabled {
  cursor: default;
}

/* Layout 2 colonnes */
.layout {
  display: grid;
  grid-template-columns: 1.4fr 0.9fr;
  gap: 16px;
  align-items: start;
}

.panel {
  background: #fff;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 14px;
  box-shadow: var(--shadow, 0 1px 2px rgba(15, 23, 42, 0.04));
  padding: 20px 22px;
}

.panel h2 {
  margin: 0 0 8px;
  font-size: 1.05rem;
  color: var(--ink, #0f172a);
}

.lead {
  margin: 0 0 18px;
  color: var(--muted, #64748b);
  font-size: 0.92rem;
  line-height: 1.45;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--muted, #64748b);
}

.field input {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 10px;
  padding: 10px 12px;
  min-height: 44px;
  font: inherit;
  background: #fff;
}

.generate {
  width: 100%;
  border: 0;
  border-radius: 10px;
  background: var(--blue, #2563eb);
  color: #fff;
  font-weight: 700;
  padding: 12px 16px;
  min-height: 46px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.generate:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.error {
  margin: 12px 0 0;
  color: #b42318;
  background: #fee4e2;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 0.9rem;
}

.progress-block {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--line, #e2e8f0);
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.progress-head h3 {
  margin: 0;
  font-size: 0.95rem;
}

.badge {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
}

.badge.ok {
  background: #ccfbf1;
  color: #0f766e;
}

.badge.ko {
  background: #fee2e2;
  color: #b91c1c;
}

.badge.run {
  background: #ffedd5;
  color: #c2410c;
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

.progress-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-size: 0.84rem;
  color: var(--muted, #64748b);
  font-weight: 600;
}

.progress-msg {
  margin: 10px 0 0;
  font-size: 0.9rem;
  color: var(--ink, #0f172a);
}

.side-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-panel h2 {
  margin-bottom: 14px;
}

.poll-panel .poll-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.poll-panel h2 {
  margin-bottom: 0;
}

.poll-lead {
  margin: 0 0 14px;
  color: var(--muted, #64748b);
  font-size: 0.88rem;
  line-height: 1.4;
}

.poll-live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  font-weight: 700;
  color: #64748b;
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 999px;
}

.poll-live.on {
  color: #0f766e;
  background: #ccfbf1;
}

.poll-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
}

.poll-live.on .poll-pulse {
  background: #0d9488;
  box-shadow: 0 0 0 0 rgba(13, 148, 136, 0.55);
  animation: pulse 1.4s ease-out infinite;
}

.poll-panel.live {
  border-color: #99f6e4;
  background: #f0fdfa;
}

.poll-panel.fail {
  border-color: #fecaca;
  background: #fff7f7;
}

.poll-panel.muted {
  opacity: 0.92;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(13, 148, 136, 0.45); }
  70% { box-shadow: 0 0 0 8px rgba(13, 148, 136, 0); }
  100% { box-shadow: 0 0 0 0 rgba(13, 148, 136, 0); }
}

.facts {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.facts > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}

.facts > div:last-of-type {
  border-bottom: 0;
  padding-bottom: 0;
}

.facts dt {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--muted, #64748b);
}

.facts dd {
  margin: 0;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--ink, #0f172a);
}

.facts dd.ok { color: #0d9488; }
.facts dd.ko { color: #dc2626; }
.facts dd.run { color: #d97706; }

.side-msg {
  margin: 14px 0 0;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 10px;
  font-size: 0.85rem;
  color: #475569;
  line-height: 1.4;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  display: inline-block;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 860px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .topbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .session-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .tabs {
    width: 100%;
  }

  .tab {
    flex: 1;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
