<template>
  <section class="breakdown">
    <section class="global-card">
      <header class="global-head">
        <div class="global-titles">
          <p class="global-eyebrow">Vue d’ensemble</p>
          <h2>Détail global par tranche</h2>
          <p class="global-meta">{{ timeLabel }} · {{ calendarDayCount }} jour(s)</p>
        </div>
        <div v-if="kpis" class="global-chips">
          <span class="chip in">Entrées {{ formatNumber(kpis.totalEntries) }}</span>
          <span class="chip out">Sorties {{ formatNumber(kpis.totalExits) }}</span>
          <span class="chip net">Présence {{ formatSigned(kpis.netPresence) }}</span>
        </div>
        <button
          type="button"
          class="global-toggle"
          :aria-expanded="globalOpen"
          @click="globalOpen = !globalOpen"
        >
          {{ globalOpen ? 'Masquer les tranches' : 'Afficher les tranches' }}
        </button>
      </header>

      <div v-show="globalOpen" class="global-body">
        <p class="lead">
          Pour chaque tranche : bilan de chaque site sélectionné (toutes ses caméras), puis le total.
          Totaux et moyennes sur {{ calendarDayCount }} jour(s) calendaire(s).
          Vert = pic d’entrées · Rouge = pic de sorties.
        </p>
        <div class="table-wrap global-table-wrap">
          <table class="global-table">
            <thead>
              <tr>
                <th>Tranche</th>
                <th>Site</th>
                <th>Entrées</th>
                <th>Sorties</th>
                <th>Présence</th>
                <th>Moy. entrées</th>
                <th>Moy. sorties</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in expandSlotRows(hourlySlotAverages, true)"
                :key="row.key"
                :class="[peakRowClass(globalPeaks, row.hourStart), { 'slot-sum': row.isSum, 'slot-alt': row.alt }]"
              >
                <td
                  v-if="row.showHour"
                  :rowspan="row.rowspan"
                  class="tranche-cell"
                  :class="tranchePeakClass(globalPeaks, row.hourStart)"
                >
                  {{ row.hour }}
                </td>
                <td :class="row.isSum ? 'sum-label' : 'site-name'">{{ row.site }}</td>
                <td class="c-entries">{{ formatNumber(row.entries) }}</td>
                <td class="c-exits">{{ formatNumber(row.exits) }}</td>
                <td class="c-presence">{{ formatSigned(row.presence) }}</td>
                <td>{{ formatAvgCell(row.avgIn) }}</td>
                <td>{{ formatAvgCell(row.avgOut) }}</td>
              </tr>
            </tbody>
            <tfoot v-if="kpis">
              <tr>
                <td colspan="2">Total période</td>
                <td class="c-entries">{{ formatNumber(kpis.totalEntries) }}</td>
                <td class="c-exits">{{ formatNumber(kpis.totalExits) }}</td>
                <td class="c-presence">{{ formatSigned(kpis.netPresence) }}</td>
                <td colspan="2"></td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </section>

    <div class="days-toolbar">
      <p class="days-count">{{ calendarDayCount }} jour(s)</p>
      <div v-if="pageCount > 1" class="pager" aria-label="Pagination des jours">
        <button type="button" class="pager-btn" :disabled="page <= 1" @click="page -= 1">
          Précédent
        </button>
        <span class="pager-info">Page {{ page }} / {{ pageCount }}</span>
        <button type="button" class="pager-btn" :disabled="page >= pageCount" @click="page += 1">
          Suivant
        </button>
      </div>
    </div>

    <div
      v-for="day in pagedDays"
      :key="day.date"
      class="panel day-panel"
      :class="{ open: isExpanded(day.date) }"
    >
      <button
        type="button"
        class="day-head"
        :aria-expanded="isExpanded(day.date)"
        @click="toggleDay(day.date)"
      >
        <span class="day-chevron" aria-hidden="true"></span>
        <span class="day-head-main">
          <span class="day-date">{{ formatDate(day.date) }}</span>
          <span class="day-totals">
            <span class="c-entries">Entrées {{ formatNumber(day.entries) }}</span>
            <span class="c-exits">Sorties {{ formatNumber(day.exits) }}</span>
            <span class="c-presence">Présence {{ formatSigned(day.presenceEndOfSlot) }}</span>
          </span>
        </span>
      </button>

      <div v-show="isExpanded(day.date)" class="day-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Tranche</th>
                <th>Site</th>
                <th>Entrées</th>
                <th>Sorties</th>
                <th>Présence</th>
                <th>Moy. entrées</th>
                <th>Moy. sorties</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in expandSlotRows(day.hourlySlots, false)"
                :key="`${day.date}-${row.key}`"
                :class="[peakRowClass(dayPeaks(day), row.hourStart), { 'slot-sum': row.isSum, 'slot-alt': row.alt }]"
              >
                <td
                  v-if="row.showHour"
                  :rowspan="row.rowspan"
                  class="tranche-cell"
                  :class="tranchePeakClass(dayPeaks(day), row.hourStart)"
                >
                  {{ row.hour }}
                </td>
                <td :class="row.isSum ? 'sum-label' : 'site-name'">{{ row.site }}</td>
                <td class="c-entries">{{ formatNumber(row.entries) }}</td>
                <td class="c-exits">{{ formatNumber(row.exits) }}</td>
                <td class="c-presence">{{ formatSigned(row.presence) }}</td>
                <td>{{ formatAvgCell(row.avgIn) }}</td>
                <td>{{ formatAvgCell(row.avgOut) }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td colspan="2">Total jour</td>
                <td class="c-entries">{{ formatNumber(day.entries) }}</td>
                <td class="c-exits">{{ formatNumber(day.exits) }}</td>
                <td class="c-presence">{{ formatSigned(day.presenceEndOfSlot) }}</td>
                <td colspan="2"></td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </div>

    <div v-if="pageCount > 1" class="pager pager-bottom" aria-label="Pagination des jours">
      <button type="button" class="pager-btn" :disabled="page <= 1" @click="page -= 1">
        Précédent
      </button>
      <span class="pager-info">Page {{ page }} / {{ pageCount }}</span>
      <button type="button" class="pager-btn" :disabled="page >= pageCount" @click="page += 1">
        Suivant
      </button>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { sortDaysNewestFirst } from '../utils/sortReportDays'

const DAYS_PER_PAGE = 10

const props = defineProps({
  dailyBreakdown: { type: Array, default: () => [] },
  hourlySlotAverages: { type: Array, default: () => [] },
  kpis: { type: Object, default: null },
  timeLabel: { type: String, default: '' },
})

const expanded = ref(new Set())
const page = ref(1)
const globalOpen = ref(true)

const sortedDays = computed(() => sortDaysNewestFirst(props.dailyBreakdown))

const calendarDayCount = computed(() => sortedDays.value.length)

const pageCount = computed(() =>
  Math.max(1, Math.ceil(calendarDayCount.value / DAYS_PER_PAGE)),
)

const pagedDays = computed(() => {
  const start = (page.value - 1) * DAYS_PER_PAGE
  return sortedDays.value.slice(start, start + DAYS_PER_PAGE)
})

const globalPeaks = computed(() =>
  peakHours(props.hourlySlotAverages, (row) => row.totalEntries, (row) => row.totalExits),
)

watch(
  sortedDays,
  (rows) => {
    page.value = 1
    expanded.value = new Set()
    if (!rows?.length) return
    if (rows.length === 1) {
      expanded.value.add(rows[0].date)
    }
  },
  { immediate: true },
)

watch(pageCount, (count) => {
  if (page.value > count) page.value = count
})

function isExpanded(date) {
  return expanded.value.has(date)
}

function toggleDay(date) {
  const next = new Set(expanded.value)
  if (next.has(date)) {
    next.delete(date)
  } else {
    next.add(date)
  }
  expanded.value = next
}

function peakHours(rows, getIn, getOut) {
  const list = rows || []
  let maxIn = 0
  let maxOut = 0
  for (const row of list) {
    maxIn = Math.max(maxIn, Number(getIn(row) ?? 0))
    maxOut = Math.max(maxOut, Number(getOut(row) ?? 0))
  }
  return {
    inHours: new Set(
      list.filter((row) => maxIn > 0 && Number(getIn(row) ?? 0) === maxIn).map((row) => row.hourStart),
    ),
    outHours: new Set(
      list.filter((row) => maxOut > 0 && Number(getOut(row) ?? 0) === maxOut).map((row) => row.hourStart),
    ),
  }
}

function dayPeaks(day) {
  return peakHours(day?.hourlySlots, (slot) => slot.entries, (slot) => slot.exits)
}

function peakRowClass(peaks, hourStart) {
  return {
    'peak-in': peaks.inHours.has(hourStart),
    'peak-out': peaks.outHours.has(hourStart),
  }
}

function tranchePeakClass(peaks, hourStart) {
  return {
    'tranche-peak-in': peaks.inHours.has(hourStart),
    'tranche-peak-out': peaks.outHours.has(hourStart),
  }
}

function expandSlotRows(slots, global) {
  const lines = []
  ;(slots || []).forEach((slot, slotIndex) => {
    const sites = slot.sites || []
    const hour = `${slot.hourStart} – ${slot.hourEnd}`
    const summedIn = sites.reduce((sum, site) => sum + Number(site.entries ?? 0), 0)
    const summedOut = sites.reduce((sum, site) => sum + Number(site.exits ?? 0), 0)
    const totalIn = sites.length
      ? summedIn
      : Number(global ? slot.totalEntries : slot.entries) || 0
    const totalOut = sites.length
      ? summedOut
      : Number(global ? slot.totalExits : slot.exits) || 0
    const span = sites.length + 1
    const alt = slotIndex % 2 === 1
    sites.forEach((site, i) => {
      const entries = Number(site.entries ?? 0)
      const exits = Number(site.exits ?? 0)
      lines.push({
        key: `${slot.hourStart}-${site.siteId ?? site.siteName}-${i}`,
        hourStart: slot.hourStart,
        hour,
        showHour: i === 0,
        rowspan: i === 0 ? span : 0,
        site: site.siteName || 'Site',
        entries,
        exits,
        presence: entries - exits,
        avgIn: site.averageEntries ?? null,
        avgOut: site.averageExits ?? null,
        isSum: false,
        alt,
      })
    })
    const presence = totalIn - totalOut
    lines.push({
      key: `${slot.hourStart}-total`,
      hourStart: slot.hourStart,
      hour,
      showHour: sites.length === 0,
      rowspan: sites.length === 0 ? 1 : 0,
      site: 'Total',
      entries: totalIn,
      exits: totalOut,
      presence,
      avgIn: slot.averageEntries ?? null,
      avgOut: slot.averageExits ?? null,
      isSum: true,
      alt,
    })
  })
  return lines
}

function formatAvgCell(n) {
  if (n == null || n === '') return ''
  return formatAvg(n)
}

function formatNumber(n) {
  return new Intl.NumberFormat('fr-FR').format(n ?? 0)
}

function formatAvg(n) {
  const value = Number(n ?? 0)
  return new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(
    value,
  )
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
</script>

<style scoped>
.breakdown {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 14px 16px;
}

.global-card {
  background: #fff;
  border: 1px solid #1e3a5f;
  border-radius: 16px;
  box-shadow: var(--shadow);
  overflow: hidden;
}

.global-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px 18px;
  padding: 16px 18px;
  background: linear-gradient(135deg, #0f172a 0%, #1e3a5f 100%);
  color: #fff;
}

.global-titles {
  flex: 1 1 220px;
  min-width: 0;
}

.global-eyebrow {
  margin: 0 0 4px;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #93c5fd;
}

.global-titles h2 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: #fff;
}

.global-meta {
  margin: 4px 0 0;
  font-size: 0.82rem;
  color: #cbd5e1;
}

.global-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.global-chips .chip {
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 0.78rem;
  font-weight: 700;
}

.global-chips .chip.in {
  background: #d1fae5;
  color: #047857;
}

.global-chips .chip.out {
  background: #fee2e2;
  color: #b91c1c;
}

.global-chips .chip.net {
  background: #e0f2fe;
  color: #075985;
}

.global-toggle {
  margin-left: auto;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
}

.global-toggle:hover {
  background: rgba(255, 255, 255, 0.16);
}

.global-body {
  padding: 14px 16px 16px;
  background: #f8fafc;
}

.global-table-wrap {
  background: #fff;
  border: 1px solid var(--line);
}

.global-table thead th {
  background: #0f172a;
}

.lead {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 0.85rem;
}

.days-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.days-count {
  margin: 0;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--muted);
}

.pager {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pager-bottom {
  justify-content: center;
  padding-bottom: 4px;
}

.pager-btn {
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
  color: var(--ink);
}

.pager-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pager-info {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--muted);
  min-width: 88px;
  text-align: center;
}

.day-panel {
  padding: 0;
  overflow: hidden;
}

.day-head {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 0;
  background: #f8fafc;
  cursor: pointer;
  text-align: left;
}

.day-panel.open .day-head {
  border-bottom: 1px solid var(--line);
}

.day-chevron {
  width: 8px;
  height: 8px;
  border-right: 2px solid var(--muted);
  border-bottom: 2px solid var(--muted);
  transform: rotate(-45deg);
  transition: transform 0.15s ease;
  flex-shrink: 0;
  margin-top: -2px;
}

.day-panel.open .day-chevron {
  transform: rotate(45deg);
  margin-top: 2px;
}

.day-head-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.day-date {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--ink);
}

.day-totals {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 0.84rem;
  font-weight: 600;
}

.day-body {
  padding: 12px 14px 14px;
}

.table-wrap {
  overflow: auto;
  border-radius: 10px;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

thead th {
  background: var(--navy);
  color: #fff;
  text-align: left;
  padding: 10px;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

td {
  padding: 9px 10px;
  border-bottom: 1px solid var(--line);
}

tfoot td {
  font-weight: 700;
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

tr.peak-in.slot-sum td {
  background: #d1fae5;
}

tr.peak-out.slot-sum td {
  background: #fee2e2;
}

tr.peak-in.peak-out.slot-sum td {
  background: linear-gradient(90deg, #d1fae5 50%, #fee2e2 50%);
}

tr.slot-alt:not(.slot-sum) td {
  background: #f8fafc;
}

tr.peak-in:not(.slot-sum) td,
tr.peak-out:not(.slot-sum) td {
  background: transparent;
}

td.tranche-cell {
  font-weight: 700;
  vertical-align: middle;
  white-space: nowrap;
  background: #fff;
  border-right: 1px solid var(--line);
}

tr.slot-alt td.tranche-cell,
tr.peak-in:not(.slot-sum) td.tranche-cell,
tr.peak-out:not(.slot-sum) td.tranche-cell {
  background: #fff;
}

tr.slot-alt td.tranche-cell {
  background: #f8fafc;
}

td.tranche-cell.tranche-peak-in {
  box-shadow: inset 3px 0 0 #047857;
}

td.tranche-cell.tranche-peak-out {
  box-shadow: inset 3px 0 0 #b91c1c;
}

td.site-name {
  font-weight: 600;
  color: #334155;
}

tr.slot-sum td {
  font-weight: 700;
  background: #e8eef5;
  border-bottom: 2px solid #94a3b8;
}
</style>
