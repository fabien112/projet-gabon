<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-2xl font-bold text-gray-800">Tableau de bord</h2>
      <span class="text-sm text-gray-500">{{ today }}</span>
    </div>

    <!-- Cartes stats -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
      <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
        <p class="text-sm text-gray-500">Présents aujourd'hui</p>
        <p class="text-3xl font-bold text-green-600 mt-1">{{ store.stats?.present ?? '—' }}</p>
      </div>
      <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
        <p class="text-sm text-gray-500">Absents</p>
        <p class="text-3xl font-bold text-red-500 mt-1">{{ store.stats?.absent ?? '—' }}</p>
      </div>
      <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
        <p class="text-sm text-gray-500">Retards</p>
        <p class="text-3xl font-bold text-orange-500 mt-1">{{ store.stats?.late ?? '—' }}</p>
      </div>
      <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
        <p class="text-sm text-gray-500">Total employés</p>
        <p class="text-3xl font-bold text-blue-600 mt-1">{{ store.stats?.total ?? '—' }}</p>
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-6">
      <!-- Flux temps réel (SSE) -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-100">
        <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div class="flex items-center gap-2">
            <span class="relative flex h-2.5 w-2.5">
              <span v-if="store.sseConnected"
                class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
              <span :class="store.sseConnected ? 'bg-green-500' : 'bg-gray-300'"
                class="relative inline-flex rounded-full h-2.5 w-2.5"></span>
            </span>
            <h3 class="font-semibold text-gray-700">Badges en direct</h3>
          </div>
          <span class="text-xs text-gray-400">
            {{ store.sseConnected ? 'En direct' : 'Reconnexion…' }}
          </span>
        </div>

        <div class="divide-y divide-gray-50 max-h-96 overflow-y-auto">
          <transition-group name="badge-slide" tag="div">
            <div v-for="b in store.liveBadges" :key="`${b.personId}-${b.swipeTime}`"
              class="flex items-center gap-4 px-5 py-3 hover:bg-gray-50">
              <span :class="isEntry(b.eventType) ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
                class="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold">
                {{ isEntry(b.eventType) ? 'IN' : 'OUT' }}
              </span>
              <div class="flex-1 min-w-0">
                <p class="font-medium text-gray-800 truncate">{{ b.personName || b.personId }}</p>
                <p class="text-xs text-gray-400 truncate">{{ b.deptName }}</p>
              </div>
              <div class="text-right flex-shrink-0">
                <p class="text-sm font-semibold"
                  :class="isEntry(b.eventType) ? 'text-green-600' : 'text-red-500'">
                  {{ b.eventName || eventLabel(b.eventType) }}
                </p>
                <p class="text-xs text-gray-400">{{ formatHour(b.swipeTime) }}</p>
              </div>
            </div>
          </transition-group>

          <div v-if="store.liveBadges.length === 0"
            class="px-5 py-10 text-center text-gray-400 text-sm">
            En attente de badges…
          </div>
        </div>
      </div>

      <!-- Historique récent -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-100">
        <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <h3 class="font-semibold text-gray-700">Historique du jour</h3>
          <button @click="refreshHistory"
            class="text-sm text-blue-600 hover:underline">Actualiser</button>
        </div>
        <table class="w-full text-sm">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Employé</th>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Heure</th>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Type</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in store.records" :key="r.id"
              class="border-t border-gray-50 hover:bg-gray-50">
              <td class="px-4 py-3 font-medium truncate max-w-[140px]">{{ r.person_name }}</td>
              <td class="px-4 py-3 text-gray-600">{{ formatHour(r.swipe_time) }}</td>
              <td class="px-4 py-3">
                <span :class="isEntry(r.event_type) ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
                  class="px-2 py-1 rounded-full text-xs font-medium">
                  {{ r.event_name || eventLabel(r.event_type) }}
                </span>
              </td>
            </tr>
            <tr v-if="store.records.length === 0">
              <td colspan="3" class="px-4 py-8 text-center text-gray-400">
                Aucun pointage enregistré aujourd'hui.
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="store.stats?.syncedAt" class="text-xs text-gray-400 px-4 py-2 text-right border-t border-gray-50">
          Dernière sync : {{ formatTime(store.stats.syncedAt) }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import dayjs from 'dayjs';
import { useAttendanceStore } from '../stores/attendance';

const store = useAttendanceStore();
const today = dayjs().format('dddd DD MMMM YYYY');

const EVENT_LABELS = { '0': 'Tous', '1': 'Entrée', '2': 'Sortie', '3': 'Sortie ext.', '4': 'Retour' };
const isEntry    = t => t === '1' || t === '4';
const eventLabel = t => EVENT_LABELS[t] ?? t;
const formatHour = t => t ? dayjs(t).format('HH:mm:ss') : '—';
const formatTime = t => t ? dayjs(t).format('DD/MM HH:mm') : '—';

async function refreshHistory() {
  await Promise.all([
    store.fetchStats(),
    store.fetchRecords({ pageSize: 20 }),
  ]);
}

onMounted(refreshHistory);
</script>

<style scoped>
.badge-slide-enter-active {
  transition: all 0.3s ease;
}
.badge-slide-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
