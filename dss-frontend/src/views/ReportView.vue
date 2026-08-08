<template>
  <div class="page">
    <header class="topbar">
      <div class="title-wrap">
        <span class="logo" aria-hidden="true">👥</span>
        <h1>People Counting – Rapport personnalisé</h1>
      </div>
      <div class="top-actions">
        <button
          type="button"
          class="btn-outline excel"
          :disabled="!report || exporting"
          @click="exportExcelReport"
        >
          ⤓ Exporter Excel
        </button>
        <button
          type="button"
          class="btn-outline pdf"
          :disabled="!report || exporting"
          @click="exportPdfReport"
        >
          ⤓ Exporter PDF
        </button>
        <button type="button" class="btn-icon" title="Aujourd'hui" @click="setToday">📅</button>
        <button type="button" class="btn-outline" @click="doLogout">Déconnexion</button>
      </div>
    </header>

    <section class="filters">
      <label class="filter-item">
        <span class="label">Période</span>
        <div class="combo">
          <span class="combo-ico">📅</span>
          <input v-model="filters.from" type="date" :max="filters.to" @change="onPeriodChange" />
          <span class="arrow">→</span>
          <input v-model="filters.to" type="date" :min="filters.from" @change="onPeriodChange" />
        </div>
      </label>

      <label class="filter-item">
        <span class="label">Tranche horaire</span>
        <div class="combo">
          <span class="combo-ico">🕒</span>
          <select v-model="filters.timeFrom" @change="onTimeChange">
            <option v-for="h in hourOptionsStart" :key="'from-' + h" :value="h">{{ h }}</option>
          </select>
          <span class="arrow">→</span>
          <select v-model="filters.timeTo" @change="onTimeChange">
            <option
              v-for="h in hourOptionsEnd"
              :key="'to-' + h"
              :value="h"
              :disabled="!isTimeToAllowed(h)"
            >
              {{ h }}
            </option>
          </select>
        </div>
      </label>

      <label class="filter-item">
        <span class="label">Caméra</span>
        <CameraMultiSelect
          v-model="selectedCameras"
          :options="cameras"
          :max="MAX_CAMERAS"
          :invalid="!cameraSelectionValid"
        />
      </label>

      <label class="filter-item">
        <span class="label">Groupe par</span>
        <select v-model="filters.groupBy" class="single">
          <option value="Jour">Jour</option>
        </select>
      </label>

      <button class="generate" :disabled="loading || !filtersValid" @click="loadReport">
        <span v-if="loading" class="spinner" aria-hidden="true"></span>
        {{ loading ? 'Exploration…' : 'Explorer' }}
      </button>
    </section>

    <p v-if="filterError" class="error">{{ filterError }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="loading" class="loading-banner">
      <span class="spinner" aria-hidden="true"></span>
      Recherche des données en cours…
    </div>

    <p v-if="report" class="result-banner">
      <span class="info-ico">ℹ</span>
      Résultat de {{ formatDate(filters.from) }} à {{ formatDate(filters.to) }}
      pour la tranche horaire {{ filters.timeFrom }} - {{ displayTimeTo }}
      · {{ selectedCameraLabel }}
    </p>

    <section v-if="report" class="kpis">
      <article class="kpi entries">
        <div class="kpi-ico">🚪↑</div>
        <div>
          <p class="kpi-title">Total Entrées</p>
          <strong>{{ formatNumber(report.kpis.totalEntries) }}</strong>
          <span>personnes</span>
        </div>
      </article>
      <article class="kpi exits">
        <div class="kpi-ico">🚪↓</div>
        <div>
          <p class="kpi-title">Total Sorties</p>
          <strong>{{ formatNumber(report.kpis.totalExits) }}</strong>
          <span>personnes</span>
        </div>
      </article>
      <article class="kpi presence">
        <div class="kpi-ico">👥</div>
        <div>
          <p class="kpi-title">Présence moyenne</p>
          <strong>{{ formatNumber(report.kpis.averageDailyPresence) }}</strong>
          <span>personnes</span>
        </div>
      </article>
      <article class="kpi period">
        <p class="kpi-title">Période sélectionnée</p>
        <strong>{{ formatDate(filters.from) }} → {{ formatDate(filters.to) }}</strong>
        <span>{{ filters.timeFrom }} → {{ displayTimeTo }}</span>
      </article>
    </section>

    <section v-if="report" class="grid">
      <div class="panel table-panel">
        <h2>Détail par jour ({{ filters.timeFrom }} - {{ displayTimeTo }})</h2>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Entrées</th>
                <th>Sorties</th>
                <th>Présence (fin tranche)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in report.dailyDetails" :key="row.date">
                <td>{{ formatDate(row.date) }}</td>
                <td class="c-entries">{{ formatNumber(row.entries) }}</td>
                <td class="c-exits">{{ formatNumber(row.exits) }}</td>
                <td class="c-presence">{{ formatSigned(row.presenceEndOfSlot) }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td>Total période</td>
                <td class="c-entries">{{ formatNumber(report.kpis.totalEntries) }}</td>
                <td class="c-exits">{{ formatNumber(report.kpis.totalExits) }}</td>
                <td class="c-presence">{{ formatSigned(report.kpis.netPresence) }}</td>
              </tr>
            </tfoot>
          </table>
        </div>
        <p class="footnote">Nombre de jours : {{ report.kpis.dayCount }}</p>
      </div>

      <div class="charts">
        <div class="panel chart-panel">
          <h2>Évolution quotidienne ({{ filters.timeFrom }} - {{ displayTimeTo }})</h2>
          <DailyLineChart :points="report.dailyEvolution" />
        </div>
        <div class="panel chart-panel">
          <h2>Répartition par jour de la semaine ({{ filters.timeFrom }} - {{ displayTimeTo }})</h2>
          <WeekdayBarChart :rows="report.weekdayDistribution" />
        </div>
      </div>
    </section>

    <p v-else-if="!loading && !error" class="empty">
      Choisissez une période puis cliquez sur « Explorer ».
    </p>

    <div v-if="loading" class="loading-overlay" aria-live="polite">
      <div class="loading-card">
        <span class="spinner big" aria-hidden="true"></span>
        <p>Exploration en cours…</p>
        <small>Récupération des comptages pour la période sélectionnée</small>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { logoutApp } from '../api/auth'
import { fetchCameras, fetchPersonalizedReport, fetchReportStatus } from '../api/reports'
import DailyLineChart from '../components/DailyLineChart.vue'
import WeekdayBarChart from '../components/WeekdayBarChart.vue'
import CameraMultiSelect from '../components/CameraMultiSelect.vue'
import { exportExcel, exportPdf } from '../utils/exportReport'

const MAX_CAMERAS = 3

const today = new Date()
const router = useRouter()
const iso = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
const monthAgo = new Date(today)
monthAgo.setDate(today.getDate() - 30)

const filters = reactive({
  from: iso(monthAgo),
  to: iso(today),
  timeFrom: '09:00',
  timeTo: '11:00',
  groupBy: 'Jour',
})

const selectedCameras = ref([])

const hourOptionsStart = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`)
const hourOptionsEnd = [
  ...Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`).slice(1),
  '24:00',
]

const cameras = ref([])
const report = ref(null)
const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const filterError = ref('')
const dbStatus = reactive({ cameras: 0, hourlySlots: 0 })

const displayTimeTo = computed(() => (filters.timeTo === '24:00' ? '00:00' : filters.timeTo))

const cameraSelectionValid = computed(
  () => selectedCameras.value.length >= 1 && selectedCameras.value.length <= MAX_CAMERAS,
)

const allCamerasSelected = computed(
  () =>
    cameras.value.length > 0 &&
    selectedCameras.value.length === cameras.value.length &&
    cameras.value.every((c) => selectedCameras.value.includes(c.channelId)),
)

const selectedCameraLabel = computed(() => {
  const names = selectedCameras.value.map(
    (id) => cameras.value.find((c) => c.channelId === id)?.name || id,
  )
  if (names.length === 0) return 'Aucune caméra'
  if (allCamerasSelected.value) return `Toutes les caméras (${names.length})`
  if (names.length === 1) return `Caméra : ${names[0]}`
  return `${names.length} caméras : ${names.join(', ')}`
})

const filtersValid = computed(() => {
  if (!filters.from || !filters.to || !filters.timeFrom || !filters.timeTo) return false
  if (filters.to < filters.from) return false
  if (!cameraSelectionValid.value) return false
  return timeToMinutes(filters.timeTo) > timeToMinutes(filters.timeFrom)
})

function timeToMinutes(value) {
  if (value === '24:00') return 24 * 60
  const [h, m] = value.split(':').map(Number)
  return h * 60 + m
}

function isTimeToAllowed(value) {
  return timeToMinutes(value) > timeToMinutes(filters.timeFrom)
}

function defaultCameraSelection(cams) {
  if (!cams?.length) return []
  return cams.slice(0, MAX_CAMERAS).map((c) => c.channelId)
}

function onPeriodChange() {
  filterError.value = ''
  if (filters.to < filters.from) {
    filters.to = filters.from
    filterError.value = 'La date de fin ne peut pas être antérieure à la date de début.'
  }
}

function onTimeChange() {
  filterError.value = ''
  if (timeToMinutes(filters.timeTo) <= timeToMinutes(filters.timeFrom)) {
    const next = hourOptionsEnd.find((h) => timeToMinutes(h) > timeToMinutes(filters.timeFrom))
    filters.timeTo = next || '24:00'
    filterError.value = "L'heure de fin doit être après l'heure de début."
  }
}

function setToday() {
  const todayIso = iso(new Date())
  filters.from = todayIso
  filters.to = todayIso
  filters.timeFrom = '00:00'
  filters.timeTo = '24:00'
  filterError.value = ''
}

async function doLogout() {
  try {
    await logoutApp()
  } catch {
    // ignore
  }
  router.replace('/login')
}

async function runExport(kind, exporter) {
  if (!report.value) {
    error.value = 'Aucun rapport à exporter. Cliquez d’abord sur « Explorer ».'
    return
  }
  exporting.value = true
  error.value = ''
  try {
    await exporter({
      report: report.value,
      filters: { ...filters, cameras: [...selectedCameras.value] },
      cameras: cameras.value,
    })
  } catch (e) {
    error.value = e?.message || `Échec de l'export ${kind}.`
  } finally {
    exporting.value = false
  }
}

function exportExcelReport() {
  return runExport('Excel', exportExcel)
}

function exportPdfReport() {
  return runExport('PDF', exportPdf)
}

function formatNumber(n) {
  return new Intl.NumberFormat('fr-FR').format(n ?? 0)
}

function formatSigned(n) {
  const value = n ?? 0
  const formatted = formatNumber(Math.abs(value))
  return value > 0 ? `+${formatted}` : value < 0 ? `-${formatted}` : '0'
}

function formatDate(isoDate) {
  const [y, m, d] = isoDate.split('-')
  return `${d}/${m}/${y}`
}

async function refreshMeta() {
  try {
    const [cams, status] = await Promise.all([fetchCameras(), fetchReportStatus()])
    cameras.value = cams
    dbStatus.cameras = status.cameras
    dbStatus.hourlySlots = status.hourlySlots
    const known = new Set(cams.map((c) => c.channelId))
    const kept = selectedCameras.value.filter((id) => known.has(id)).slice(0, MAX_CAMERAS)
    selectedCameras.value = kept.length > 0 ? kept : defaultCameraSelection(cams)
  } catch (e) {
    error.value = 'Backend inaccessible. Démarrez dss-integration sur le port 8080.'
  }
}

async function loadReport() {
  if (!cameraSelectionValid.value) {
    filterError.value = 'Sélectionnez entre 1 et 3 caméra(s).'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const cams =
      allCamerasSelected.value && cameras.value.length > 0
        ? 'all'
        : [...selectedCameras.value]
    report.value = await fetchPersonalizedReport({
      from: filters.from,
      to: filters.to,
      timeFrom: filters.timeFrom,
      timeTo: filters.timeTo === '23:59' ? '24:00' : filters.timeTo,
      cameras: cams === 'all' ? ['all'] : cams,
      groupBy: filters.groupBy,
    })
    await refreshMeta()
  } catch (e) {
    error.value = e?.response?.data?.message || e.message || 'Erreur chargement rapport'
    report.value = null
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await refreshMeta()
  if (dbStatus.hourlySlots > 0) {
    await loadReport()
  }
})
</script>

<style scoped>
.page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 20px 20px 40px;
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
  background: var(--blue-soft);
  display: grid;
  place-items: center;
  font-size: 1rem;
}

h1 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--ink);
}

.top-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.btn-outline,
.btn-icon {
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.85rem;
  text-decoration: none;
  color: inherit;
  display: inline-flex;
  align-items: center;
}

.btn-outline.excel {
  color: var(--green);
}

.btn-outline.pdf {
  color: #dc2626;
}

.btn-outline:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-icon {
  width: 38px;
  height: 38px;
  padding: 0;
}

.filters {
  display: grid;
  grid-template-columns: 1.35fr 1.1fr 0.95fr 0.7fr auto;
  gap: 12px;
  align-items: end;
  background: #eef2f6;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 14px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.label {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--muted);
}

.combo {
  display: grid;
  grid-template-columns: auto 1fr auto 1fr;
  gap: 6px;
  align-items: center;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 6px 8px;
  min-height: 44px;
}

.combo-ico {
  font-size: 0.85rem;
  opacity: 0.8;
}

.arrow {
  color: var(--muted);
  font-weight: 700;
}

.combo input,
.combo select,
.single {
  width: 100%;
  border: 0;
  background: transparent;
  padding: 6px 4px;
  min-width: 0;
  outline: none;
}

.single {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 12px;
  min-height: 44px;
}

.generate {
  border: 0;
  border-radius: 10px;
  background: var(--blue);
  color: #fff;
  font-weight: 700;
  padding: 12px 16px;
  min-height: 44px;
  cursor: pointer;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.generate:disabled {
  opacity: 0.7;
  cursor: wait;
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

.spinner.big {
  width: 28px;
  height: 28px;
  border-width: 3px;
  border-color: rgba(37, 99, 235, 0.25);
  border-top-color: var(--blue);
}

.loading-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  color: var(--blue);
  font-weight: 600;
  font-size: 0.92rem;
}

.loading-banner .spinner {
  border-color: rgba(37, 99, 235, 0.25);
  border-top-color: var(--blue);
}

.loading-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.28);
  display: grid;
  place-items: center;
  z-index: 50;
}

.loading-card {
  background: #fff;
  border-radius: 14px;
  padding: 22px 28px;
  box-shadow: var(--shadow);
  text-align: center;
  min-width: 260px;
}

.loading-card p {
  margin: 12px 0 4px;
  font-weight: 700;
}

.loading-card small {
  color: var(--muted);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.result-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 0;
  color: var(--blue);
  font-size: 0.9rem;
  font-weight: 500;
}

.info-ico {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--blue-soft);
  color: var(--blue);
  display: grid;
  place-items: center;
  font-size: 0.75rem;
  font-weight: 700;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0;
}

.kpi {
  display: flex;
  gap: 12px;
  align-items: center;
  border-radius: var(--radius);
  border: 1px solid var(--line);
  padding: 14px 16px;
  background: #fff;
  box-shadow: var(--shadow);
}

.kpi-ico {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: #fff;
  flex-shrink: 0;
}

.kpi-title {
  margin: 0;
  color: var(--muted);
  font-size: 0.82rem;
  font-weight: 600;
}

.kpi strong {
  display: block;
  margin-top: 2px;
  font-size: 1.65rem;
  line-height: 1.1;
}

.kpi span {
  color: var(--muted);
  font-size: 0.8rem;
}

.kpi.entries {
  background: var(--green-soft);
}

.kpi.entries strong {
  color: var(--green);
}

.kpi.exits {
  background: var(--violet-soft);
}

.kpi.exits strong {
  color: var(--violet);
}

.kpi.presence {
  background: var(--teal-soft);
}

.kpi.presence strong {
  color: var(--teal);
}

.kpi.period {
  display: block;
}

.kpi.period strong {
  font-size: 1rem;
  margin-top: 8px;
  color: var(--ink);
}

.grid {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  gap: 14px;
}

.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 14px 16px;
}

.panel h2 {
  margin: 0 0 12px;
  font-size: 0.98rem;
  color: var(--ink);
}

.table-wrap {
  overflow: auto;
  max-height: 460px;
  border-radius: 10px;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

thead th {
  background: var(--navy);
  color: #fff;
  text-align: left;
  padding: 11px 10px;
  font-weight: 600;
  position: sticky;
  top: 0;
}

td {
  padding: 10px;
  border-bottom: 1px solid var(--line);
}

tfoot td {
  font-weight: 700;
  border-top: 2px solid var(--line);
  background: #f8fafc;
}

.c-entries {
  color: var(--green);
  font-weight: 700;
}

.c-exits {
  color: var(--violet);
  font-weight: 700;
}

.c-presence {
  color: var(--navy);
  font-weight: 700;
}

.footnote {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 0.84rem;
}

.charts {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.error {
  margin: 10px 0 0;
  color: #b42318;
  background: #fee4e2;
  border-radius: 10px;
  padding: 10px 12px;
}

.empty {
  text-align: center;
  color: var(--muted);
  padding: 48px 0;
}

@media (max-width: 1100px) {
  .filters,
  .kpis,
  .grid {
    grid-template-columns: 1fr;
  }

  .topbar {
    flex-direction: column;
    align-items: start;
  }
}
</style>
