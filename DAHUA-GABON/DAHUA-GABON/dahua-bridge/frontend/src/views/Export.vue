<template>
  <div class="p-6">
    <h2 class="text-2xl font-bold text-gray-800 mb-6">Export Sage X3</h2>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">

      <!-- Export mensuel -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <h3 class="font-semibold text-gray-700 mb-4">Exporter les pointages du mois</h3>
        <div class="flex gap-3 mb-4">
          <select v-model="year" class="border border-gray-200 rounded-lg px-3 py-2 text-sm">
            <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
          </select>
          <select v-model="month" class="border border-gray-200 rounded-lg px-3 py-2 text-sm">
            <option v-for="m in months" :key="m.v" :value="m.v">{{ m.l }}</option>
          </select>
        </div>
        <button @click="doExport" :disabled="exporting"
          class="w-full bg-green-600 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-60">
          {{ exporting ? 'Export en cours...' : '📤 Exporter vers Sage X3' }}
        </button>
        <div v-if="exportResult" class="mt-4 p-3 rounded-lg text-sm"
          :class="exportResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-600'">
          <div v-if="exportResult.success">
            ✅ {{ exportResult.records }} pointages exportés<br />
            📁 {{ exportResult.filePath }}
          </div>
          <div v-else>❌ {{ exportResult.message || exportResult.error }}</div>
        </div>
      </div>

      <!-- Synchronisation manuelle -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <h3 class="font-semibold text-gray-700 mb-4">Synchronisation DSS</h3>
        <p class="text-sm text-gray-500 mb-4">
          Force une synchronisation immédiate des pointages depuis DSS Pro.
          La sync automatique tourne toutes les 5 minutes.
        </p>
        <button @click="doSync" :disabled="syncing"
          class="w-full bg-blue-600 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60">
          {{ syncing ? 'Synchronisation...' : '🔄 Synchroniser maintenant' }}
        </button>
        <div v-if="syncResult" class="mt-4 p-3 rounded-lg text-sm"
          :class="syncResult.status === 'success' ? 'bg-blue-50 text-blue-700' : 'bg-red-50 text-red-600'">
          {{ syncResult.status === 'success'
            ? `✅ ${syncResult.recordsNew} nouveaux pointages synchronisés`
            : `❌ ${syncResult.message}` }}
        </div>
      </div>
    </div>

    <!-- Historique des synchronisations -->
    <div class="mt-6 bg-white rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
        <h3 class="font-semibold text-gray-700">Historique des synchronisations</h3>
        <button @click="store.fetchSyncLog()" class="text-sm text-blue-600 hover:underline">Actualiser</button>
      </div>
      <table class="w-full text-sm">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Date</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Statut</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Nouveaux</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Message</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in store.syncLog" :key="l.id" class="border-t border-gray-50">
            <td class="px-4 py-2.5 text-gray-600">{{ fmt(l.synced_at) }}</td>
            <td class="px-4 py-2.5">
              <span :class="l.status === 'success' ? 'text-green-600' : 'text-red-500'" class="font-medium">
                {{ l.status === 'success' ? '✅ OK' : '❌ Erreur' }}
              </span>
            </td>
            <td class="px-4 py-2.5 text-gray-600">{{ l.records_new }}</td>
            <td class="px-4 py-2.5 text-gray-400 text-xs">{{ l.message }}</td>
          </tr>
          <tr v-if="store.syncLog.length === 0">
            <td colspan="4" class="px-4 py-8 text-center text-gray-400">Aucun historique.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import dayjs from 'dayjs';
import { useAttendanceStore } from '../stores/attendance';

const store  = useAttendanceStore();
const year   = ref(dayjs().year());
const month  = ref(dayjs().month() + 1);
const exporting   = ref(false);
const syncing     = ref(false);
const exportResult = ref(null);
const syncResult   = ref(null);

const years  = Array.from({ length: 5 }, (_, i) => dayjs().year() - i);
const months = [
  { v: 1, l: 'Janvier' }, { v: 2, l: 'Février' },   { v: 3, l: 'Mars' },
  { v: 4, l: 'Avril' },   { v: 5, l: 'Mai' },        { v: 6, l: 'Juin' },
  { v: 7, l: 'Juillet' }, { v: 8, l: 'Août' },       { v: 9, l: 'Septembre' },
  { v: 10, l: 'Octobre' },{ v: 11, l: 'Novembre' },  { v: 12, l: 'Décembre' },
];

const fmt = t => t ? dayjs(t).format('DD/MM/YYYY HH:mm') : '—';

async function doExport() {
  exporting.value = true;
  exportResult.value = null;
  try {
    exportResult.value = await store.exportSage(year.value, month.value);
  } catch (e) {
    exportResult.value = { success: false, error: e.message };
  } finally {
    exporting.value = false;
  }
}

async function doSync() {
  syncing.value = true;
  syncResult.value = null;
  try {
    syncResult.value = await store.triggerSync();
    await store.fetchSyncLog();
  } catch (e) {
    syncResult.value = { status: 'error', message: e.message };
  } finally {
    syncing.value = false;
  }
}

onMounted(() => store.fetchSyncLog());
</script>
