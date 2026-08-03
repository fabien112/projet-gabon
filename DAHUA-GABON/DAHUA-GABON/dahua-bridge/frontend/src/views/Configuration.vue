<template>
  <div class="p-6">
    <h2 class="text-2xl font-bold text-gray-800 mb-6">Configuration</h2>

    <!-- Onglets -->
    <div class="flex gap-1 mb-6 bg-gray-100 p-1 rounded-xl w-fit">
      <button v-for="tab in tabs" :key="tab.key"
        @click="activeTab = tab.key"
        :class="activeTab === tab.key
          ? 'bg-white shadow text-blue-600 font-medium'
          : 'text-gray-500 hover:text-gray-700'"
        class="px-4 py-2 rounded-lg text-sm transition-all">
        {{ tab.label }}
      </button>
    </div>

    <!-- ── Horaires (Shifts) ─────────────────────────────── -->
    <div v-if="activeTab === 'shifts'">
      <div class="flex justify-between items-center mb-4">
        <h3 class="font-semibold text-gray-700">Horaires de travail</h3>
        <button @click="openShiftModal(null)"
          class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-700">
          + Ajouter un horaire
        </button>
      </div>
      <div class="grid gap-3">
        <div v-for="s in shifts" :key="s.id"
          class="bg-white rounded-xl border border-gray-100 shadow-sm p-4 flex items-center justify-between">
          <div>
            <div class="flex items-center gap-2">
              <span class="font-medium text-gray-800">{{ s.name }}</span>
              <span v-if="s.is_default" class="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">Défaut</span>
            </div>
            <p class="text-sm text-gray-500 mt-1">
              {{ s.start_time }} → {{ s.end_time }}
              <span v-if="s.crosses_midnight" class="ml-1 text-orange-500">(chevauchement minuit)</span>
              · {{ formatDays(s.days_of_week) }}
            </p>
          </div>
          <div class="flex gap-2">
            <button @click="openShiftModal(s)" class="text-blue-600 hover:underline text-sm">Modifier</button>
            <button @click="deleteShift(s.id)" class="text-red-500 hover:underline text-sm">Supprimer</button>
          </div>
        </div>
        <div v-if="shifts.length === 0" class="text-center text-gray-400 py-8">Aucun horaire défini</div>
      </div>
    </div>

    <!-- ── Règles de calcul ──────────────────────────────── -->
    <div v-if="activeTab === 'rules'">
      <div class="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
        <h3 class="font-semibold text-gray-700 mb-4">Règles de calcul des heures</h3>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-4">
          <div v-for="rule in rules" :key="rule.key">
            <label class="block text-sm text-gray-600 mb-1">{{ rule.label }}</label>
            <input v-model="ruleValues[rule.key]" type="text"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          </div>
        </div>
        <button @click="saveRules"
          class="mt-6 bg-blue-600 text-white px-6 py-2 rounded-lg text-sm hover:bg-blue-700">
          Enregistrer les règles
        </button>
        <span v-if="rulesSaved" class="ml-3 text-green-600 text-sm">✓ Enregistré</span>
      </div>
    </div>

    <!-- ── Jours fériés ──────────────────────────────────── -->
    <div v-if="activeTab === 'holidays'">
      <div class="flex justify-between items-center mb-4">
        <h3 class="font-semibold text-gray-700">Jours fériés</h3>
        <button @click="openHolidayModal(null)"
          class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-700">
          + Ajouter un jour férié
        </button>
      </div>
      <div class="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <table class="w-full text-sm">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Date</th>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Nom</th>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Récurrent</th>
              <th class="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="h in holidays" :key="h.id" class="border-t border-gray-50 hover:bg-gray-50">
              <td class="px-4 py-3">{{ formatDate(h.date) }}</td>
              <td class="px-4 py-3 font-medium">{{ h.name }}</td>
              <td class="px-4 py-3">
                <span :class="h.is_recurring ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'"
                  class="px-2 py-0.5 rounded-full text-xs">{{ h.is_recurring ? 'Oui' : 'Non' }}</span>
              </td>
              <td class="px-4 py-3 flex gap-2 justify-end">
                <button @click="openHolidayModal(h)" class="text-blue-600 hover:underline text-xs">Modifier</button>
                <button @click="deleteHoliday(h.id)" class="text-red-500 hover:underline text-xs">Supprimer</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ── Config Sage X3 ────────────────────────────────── -->
    <div v-if="activeTab === 'sage'">
      <div class="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
        <h3 class="font-semibold text-gray-700 mb-2">Correspondance rubriques Sage X3</h3>
        <p class="text-sm text-gray-500 mb-4">Configurez les codes rubriques que Sage X3 utilise pour importer les heures.</p>
        <table class="w-full text-sm">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Type d'heure</th>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Code rubrique Sage</th>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Libellé</th>
              <th class="px-4 py-3 text-left text-gray-500 font-medium">Actif</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cfg in sageConfig" :key="cfg.hour_type" class="border-t border-gray-50">
              <td class="px-4 py-3 text-gray-600">{{ hourTypeLabel(cfg.hour_type) }}</td>
              <td class="px-4 py-3">
                <input v-model="cfg.rubrique_code" type="text"
                  class="border border-gray-200 rounded px-2 py-1 text-sm w-40 focus:outline-none focus:ring-1 focus:ring-blue-500">
              </td>
              <td class="px-4 py-3">
                <input v-model="cfg.rubrique_label" type="text"
                  class="border border-gray-200 rounded px-2 py-1 text-sm w-48 focus:outline-none focus:ring-1 focus:ring-blue-500">
              </td>
              <td class="px-4 py-3">
                <input type="checkbox" v-model="cfg.enabled" class="rounded">
              </td>
            </tr>
          </tbody>
        </table>
        <div class="mt-4 flex items-center gap-3">
          <button @click="saveSageConfig"
            class="bg-blue-600 text-white px-6 py-2 rounded-lg text-sm hover:bg-blue-700">
            Enregistrer
          </button>
          <span v-if="sageSaved" class="text-green-600 text-sm">✓ Enregistré</span>
        </div>
        <div class="mt-6 bg-gray-50 rounded-lg p-4">
          <p class="text-xs text-gray-500 font-medium mb-2">Aperçu du format d'export CSV :</p>
          <pre class="text-xs text-gray-600">MATRICULE;CODE_RUBRIQUE;VALEUR;PERIODE;NOM
EMP001;HEURE_NORM;160.00;05/2026;Jean Dupont
EMP001;HEURE_SUP_25;8.50;05/2026;Jean Dupont
EMP001;HEURE_NUIT;12.00;05/2026;Jean Dupont</pre>
        </div>
      </div>
    </div>

    <!-- ── Modal Shift ───────────────────────────────────── -->
    <div v-if="shiftModal.open" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-white rounded-2xl shadow-xl w-full max-w-md p-6">
        <h3 class="font-bold text-lg mb-4">{{ shiftModal.data.id ? 'Modifier' : 'Ajouter' }} un horaire</h3>
        <div class="space-y-3">
          <div>
            <label class="text-sm text-gray-600">Nom</label>
            <input v-model="shiftModal.data.name" type="text" placeholder="Ex: Matin"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 mt-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="text-sm text-gray-600">Heure début</label>
              <input v-model="shiftModal.data.start_time" type="time"
                class="w-full border border-gray-200 rounded-lg px-3 py-2 mt-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
            </div>
            <div>
              <label class="text-sm text-gray-600">Heure fin</label>
              <input v-model="shiftModal.data.end_time" type="time"
                class="w-full border border-gray-200 rounded-lg px-3 py-2 mt-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
            </div>
          </div>
          <div>
            <label class="text-sm text-gray-600">Jours travaillés</label>
            <div class="flex gap-2 mt-2 flex-wrap">
              <button v-for="d in dayButtons" :key="d.val"
                @click="toggleDay(d.val)"
                :class="selectedDays.includes(d.val) ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'"
                class="px-3 py-1 rounded-full text-xs font-medium transition-colors">
                {{ d.label }}
              </button>
            </div>
          </div>
          <label class="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
            <input type="checkbox" v-model="shiftModal.data.crosses_midnight" class="rounded">
            Chevauchement minuit (ex: 23h→7h)
          </label>
          <label class="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
            <input type="checkbox" v-model="shiftModal.data.is_default" class="rounded">
            Horaire par défaut
          </label>
        </div>
        <div class="flex gap-3 mt-5 justify-end">
          <button @click="shiftModal.open = false" class="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Annuler</button>
          <button @click="saveShift" class="bg-blue-600 text-white px-5 py-2 rounded-lg text-sm hover:bg-blue-700">Enregistrer</button>
        </div>
      </div>
    </div>

    <!-- ── Modal Jour férié ──────────────────────────────── -->
    <div v-if="holidayModal.open" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-white rounded-2xl shadow-xl w-full max-w-sm p-6">
        <h3 class="font-bold text-lg mb-4">{{ holidayModal.data.id ? 'Modifier' : 'Ajouter' }} un jour férié</h3>
        <div class="space-y-3">
          <div>
            <label class="text-sm text-gray-600">Date</label>
            <input v-model="holidayModal.data.date" type="date"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 mt-1 text-sm">
          </div>
          <div>
            <label class="text-sm text-gray-600">Nom</label>
            <input v-model="holidayModal.data.name" type="text" placeholder="Ex: Fête du Travail"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 mt-1 text-sm">
          </div>
          <label class="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
            <input type="checkbox" v-model="holidayModal.data.is_recurring" class="rounded">
            Récurrent chaque année
          </label>
        </div>
        <div class="flex gap-3 mt-5 justify-end">
          <button @click="holidayModal.open = false" class="px-4 py-2 text-sm text-gray-600">Annuler</button>
          <button @click="saveHoliday" class="bg-blue-600 text-white px-5 py-2 rounded-lg text-sm hover:bg-blue-700">Enregistrer</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import axios from 'axios';
import dayjs from 'dayjs';

const api = axios.create({ baseURL: '/api' });

const activeTab = ref('shifts');
const tabs = [
  { key: 'shifts',   label: 'Horaires' },
  { key: 'rules',    label: 'Règles de calcul' },
  { key: 'holidays', label: 'Jours fériés' },
  { key: 'sage',     label: 'Export Sage X3' },
];

// ── Shifts ──────────────────────────────────────────────────
const shifts = ref([]);
const shiftModal = reactive({ open: false, data: {} });
const selectedDays = ref([]);

const dayButtons = [
  { val: '1', label: 'Lun' }, { val: '2', label: 'Mar' },
  { val: '3', label: 'Mer' }, { val: '4', label: 'Jeu' },
  { val: '5', label: 'Ven' }, { val: '6', label: 'Sam' },
  { val: '0', label: 'Dim' },
];

function formatDays(str) {
  if (!str) return '';
  const map = { '0':'Dim','1':'Lun','2':'Mar','3':'Mer','4':'Jeu','5':'Ven','6':'Sam' };
  return str.split(',').map(d => map[d]).join(', ');
}

function toggleDay(val) {
  const i = selectedDays.value.indexOf(val);
  if (i >= 0) selectedDays.value.splice(i, 1);
  else selectedDays.value.push(val);
}

function openShiftModal(s) {
  shiftModal.data = s ? { ...s } : { name:'', start_time:'08:00', end_time:'17:00', crosses_midnight:false, is_default:false, days_of_week:'1,2,3,4,5' };
  selectedDays.value = (shiftModal.data.days_of_week || '1,2,3,4,5').split(',');
  shiftModal.open = true;
}

async function saveShift() {
  shiftModal.data.days_of_week = selectedDays.value.sort().join(',');
  if (shiftModal.data.id) {
    await api.put(`/config/shifts/${shiftModal.data.id}`, shiftModal.data);
  } else {
    await api.post('/config/shifts', shiftModal.data);
  }
  shiftModal.open = false;
  await loadShifts();
}

async function deleteShift(id) {
  if (!confirm('Supprimer cet horaire ?')) return;
  await api.delete(`/config/shifts/${id}`);
  await loadShifts();
}

async function loadShifts() {
  const { data } = await api.get('/config/shifts');
  shifts.value = data.data;
}

// ── Règles ──────────────────────────────────────────────────
const rules = ref([]);
const ruleValues = reactive({});
const rulesSaved = ref(false);

async function loadRules() {
  const { data } = await api.get('/config/rules');
  rules.value = data.data;
  data.data.forEach(r => { ruleValues[r.key] = r.value; });
}

async function saveRules() {
  await api.put('/config/rules', { ...ruleValues });
  rulesSaved.value = true;
  setTimeout(() => { rulesSaved.value = false; }, 3000);
}

// ── Jours fériés ────────────────────────────────────────────
const holidays = ref([]);
const holidayModal = reactive({ open: false, data: {} });

const formatDate = d => d ? dayjs(d).format('DD/MM/YYYY') : '—';

function openHolidayModal(h) {
  holidayModal.data = h ? { ...h, date: dayjs(h.date).format('YYYY-MM-DD') } : { date: '', name: '', is_recurring: true };
  holidayModal.open = true;
}

async function saveHoliday() {
  if (holidayModal.data.id) {
    await api.put(`/config/holidays/${holidayModal.data.id}`, holidayModal.data);
  } else {
    await api.post('/config/holidays', holidayModal.data);
  }
  holidayModal.open = false;
  await loadHolidays();
}

async function deleteHoliday(id) {
  if (!confirm('Supprimer ce jour férié ?')) return;
  await api.delete(`/config/holidays/${id}`);
  await loadHolidays();
}

async function loadHolidays() {
  const { data } = await api.get('/config/holidays');
  holidays.value = data.data;
}

// ── Sage config ──────────────────────────────────────────────
const sageConfig = ref([]);
const sageSaved = ref(false);

const HOUR_TYPE_LABELS = {
  regular: 'Heures normales', overtime_25: 'Heures sup 25%',
  overtime_50: 'Heures sup 50%', overtime_100: 'Heures sup 100% (dim/fér)',
  night: 'Heures de nuit', holiday: 'Heures jours fériés', absence: 'Absences',
};
const hourTypeLabel = t => HOUR_TYPE_LABELS[t] || t;

async function loadSageConfig() {
  const { data } = await api.get('/config/sage');
  sageConfig.value = data.data;
}

async function saveSageConfig() {
  await api.put('/config/sage', sageConfig.value);
  sageSaved.value = true;
  setTimeout(() => { sageSaved.value = false; }, 3000);
}

onMounted(async () => {
  await Promise.all([loadShifts(), loadRules(), loadHolidays(), loadSageConfig()]);
});
</script>
