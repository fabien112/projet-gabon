<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-2xl font-bold text-gray-800">Anomalies de pointage</h2>
      <button @click="openManualBadge" class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-700">
        + Corriger un badge
      </button>
    </div>

    <!-- Stats anomalies -->
    <div class="grid grid-cols-3 gap-4 mb-6">
      <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
        <p class="text-sm text-gray-500">Non résolues (total)</p>
        <p class="text-3xl font-bold text-red-500 mt-1">{{ stats?.total ?? '—' }}</p>
      </div>
      <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
        <p class="text-sm text-gray-500">Aujourd'hui</p>
        <p class="text-3xl font-bold text-orange-500 mt-1">{{ stats?.today ?? '—' }}</p>
      </div>
      <div class="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
        <p class="text-sm text-gray-500">Cette semaine</p>
        <p class="text-3xl font-bold text-yellow-500 mt-1">{{ stats?.week ?? '—' }}</p>
      </div>
    </div>

    <!-- Filtres -->
    <div class="bg-white rounded-xl border border-gray-100 shadow-sm p-4 mb-4">
      <div class="flex flex-wrap gap-3">
        <select v-model="filters.type"
          class="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">Tous les types</option>
          <option v-for="t in anomalyTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
        <select v-model="filters.resolved"
          class="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">Tous</option>
          <option value="false">Non résolues</option>
          <option value="true">Résolues</option>
        </select>
        <input v-model="filters.from" type="date"
          class="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
        <input v-model="filters.to" type="date"
          class="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
        <button @click="load" class="bg-gray-800 text-white px-4 py-2 rounded-lg text-sm hover:bg-gray-700">
          Filtrer
        </button>
      </div>
    </div>

    <!-- Tableau anomalies -->
    <div class="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Date</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Employé</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Type</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Description</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Gravité</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Statut</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in anomalies" :key="a.id"
            :class="a.resolved ? 'opacity-50' : ''"
            class="border-t border-gray-50 hover:bg-gray-50">
            <td class="px-4 py-3 text-gray-600">{{ formatDate(a.work_date) }}</td>
            <td class="px-4 py-3 font-medium">{{ a.person_name || a.person_id }}</td>
            <td class="px-4 py-3">
              <span :class="typeClass(a.type)"
                class="px-2 py-0.5 rounded-full text-xs font-medium">
                {{ typeLabel(a.type) }}
              </span>
            </td>
            <td class="px-4 py-3 text-gray-500 max-w-xs truncate">{{ a.description }}</td>
            <td class="px-4 py-3">
              <span :class="severityClass(a.severity)"
                class="px-2 py-0.5 rounded-full text-xs font-medium">
                {{ a.severity }}
              </span>
            </td>
            <td class="px-4 py-3">
              <span v-if="a.resolved" class="text-green-600 text-xs">✓ Résolu</span>
              <span v-else class="text-orange-500 text-xs">En attente</span>
            </td>
            <td class="px-4 py-3">
              <button v-if="!a.resolved" @click="openResolve(a)"
                class="text-blue-600 hover:underline text-xs">Résoudre</button>
            </td>
          </tr>
          <tr v-if="anomalies.length === 0">
            <td colspan="7" class="px-4 py-10 text-center text-gray-400">
              Aucune anomalie trouvée.
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- ── Modal Résolution ──────────────────────────────── -->
    <div v-if="resolveModal.open" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-white rounded-2xl shadow-xl w-full max-w-md p-6">
        <h3 class="font-bold text-lg mb-2">Résoudre l'anomalie</h3>
        <p class="text-sm text-gray-500 mb-4">
          {{ typeLabel(resolveModal.anomaly?.type) }} — {{ resolveModal.anomaly?.person_name }}
          ({{ formatDate(resolveModal.anomaly?.work_date) }})
        </p>
        <label class="block text-sm text-gray-600 mb-1">Note de résolution</label>
        <textarea v-model="resolveModal.note" rows="3" placeholder="Expliquez la résolution..."
          class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
        </textarea>
        <div class="flex gap-3 mt-4 justify-end">
          <button @click="resolveModal.open = false" class="px-4 py-2 text-sm text-gray-600">Annuler</button>
          <button @click="confirmResolve" class="bg-green-600 text-white px-5 py-2 rounded-lg text-sm hover:bg-green-700">
            Marquer comme résolu
          </button>
        </div>
      </div>
    </div>

    <!-- ── Modal Badge Manuel ────────────────────────────── -->
    <div v-if="badgeModal.open" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-white rounded-2xl shadow-xl w-full max-w-md p-6">
        <h3 class="font-bold text-lg mb-4">Corriger / Ajouter un badge</h3>
        <div class="space-y-3">
          <div>
            <label class="text-sm text-gray-600">Employé</label>
            <select v-model="badgeModal.data.person_id"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 mt-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option value="">-- Sélectionner --</option>
              <option v-for="e in employees" :key="e.person_id" :value="e.person_id">
                {{ e.first_name }} {{ e.last_name }} ({{ e.person_id }})
              </option>
            </select>
          </div>
          <div>
            <label class="text-sm text-gray-600">Date et heure du badge</label>
            <input v-model="badgeModal.data.swipe_time" type="datetime-local"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 mt-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          </div>
          <div>
            <label class="text-sm text-gray-600">Type</label>
            <select v-model="badgeModal.data.event_type"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 mt-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option value="1">Entrée</option>
              <option value="2">Sortie</option>
            </select>
          </div>
          <div>
            <label class="text-sm text-gray-600">Motif de correction</label>
            <textarea v-model="badgeModal.data.reason" rows="2" placeholder="Ex: Oubli de badge..."
              class="w-full border border-gray-200 rounded-lg px-3 py-2 mt-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
            </textarea>
          </div>
        </div>
        <div class="flex gap-3 mt-4 justify-end">
          <button @click="badgeModal.open = false" class="px-4 py-2 text-sm text-gray-600">Annuler</button>
          <button @click="saveBadge" class="bg-blue-600 text-white px-5 py-2 rounded-lg text-sm hover:bg-blue-700">
            Enregistrer le badge
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import axios from 'axios';
import dayjs from 'dayjs';

const api = axios.create({ baseURL: '/api' });

const anomalies = ref([]);
const employees = ref([]);
const stats     = ref(null);

const filters = reactive({ type: '', resolved: 'false', from: '', to: '' });

const anomalyTypes = [
  { value: 'missing_checkin',  label: 'Badge entrée manquant' },
  { value: 'missing_checkout', label: 'Badge sortie manquant' },
  { value: 'late',             label: 'Retard' },
  { value: 'early_out',        label: 'Sortie anticipée' },
  { value: 'absent',           label: 'Absence' },
];

const TYPE_LABELS = {
  missing_checkin:  'Entrée manquante', missing_checkout: 'Sortie manquante',
  late: 'Retard', early_out: 'Sortie anticipée', absent: 'Absence', short_break: 'Pause courte',
};
const typeLabel = t => TYPE_LABELS[t] || t;
const typeClass = t => ({
  missing_checkin:  'bg-red-100 text-red-700',
  missing_checkout: 'bg-orange-100 text-orange-700',
  late:             'bg-yellow-100 text-yellow-700',
  early_out:        'bg-blue-100 text-blue-700',
  absent:           'bg-gray-100 text-gray-700',
}[t] || 'bg-gray-100 text-gray-600');

const severityClass = s => ({
  error:   'bg-red-100 text-red-700',
  warning: 'bg-orange-100 text-orange-700',
  info:    'bg-blue-100 text-blue-600',
}[s] || 'bg-gray-100 text-gray-600');

const formatDate = d => d ? dayjs(d).format('DD/MM/YYYY') : '—';

async function load() {
  const params = { ...filters };
  if (!params.type)     delete params.type;
  if (!params.resolved) delete params.resolved;
  if (!params.from)     delete params.from;
  if (!params.to)       delete params.to;

  const [aRes, sRes] = await Promise.all([
    api.get('/anomalies', { params }),
    api.get('/anomalies/stats'),
  ]);
  anomalies.value = aRes.data.data;
  stats.value     = sRes.data.data;
}

// ── Résolution ───────────────────────────────────────────────
const resolveModal = reactive({ open: false, anomaly: null, note: '' });

function openResolve(a) {
  resolveModal.anomaly = a;
  resolveModal.note = '';
  resolveModal.open = true;
}

async function confirmResolve() {
  await api.put(`/anomalies/${resolveModal.anomaly.id}/resolve`, {
    resolution_note: resolveModal.note,
    resolved_by: 'RH',
  });
  resolveModal.open = false;
  await load();
}

// ── Badge manuel ─────────────────────────────────────────────
const badgeModal = reactive({ open: false, data: {} });

function openManualBadge() {
  badgeModal.data = {
    person_id: '', event_type: '1',
    swipe_time: dayjs().format('YYYY-MM-DDTHH:mm'),
    reason: '',
  };
  badgeModal.open = true;
}

async function saveBadge() {
  await api.post('/anomalies/manual-badges', badgeModal.data);
  badgeModal.open = false;
  await load();
}

onMounted(async () => {
  const { data } = await api.get('/employees');
  employees.value = data.data || [];
  await load();
});
</script>
