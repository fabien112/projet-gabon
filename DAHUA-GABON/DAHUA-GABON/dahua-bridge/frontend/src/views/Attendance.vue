<template>
  <div class="p-6">
    <h2 class="text-2xl font-bold text-gray-800 mb-6">Pointages</h2>

    <!-- Filtres -->
    <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-4 mb-5 flex flex-wrap gap-3">
      <input type="date" v-model="filters.startDate" class="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
      <input type="date" v-model="filters.endDate"   class="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
      <select v-model="filters.deptId" class="border border-gray-200 rounded-lg px-3 py-2 text-sm">
        <option value="">Tous les départements</option>
        <option v-for="d in store.departments" :key="d.org_code" :value="d.org_code">{{ d.org_name }}</option>
      </select>
      <select v-model="filters.eventType" class="border border-gray-200 rounded-lg px-3 py-2 text-sm">
        <option value="">Tous les types</option>
        <option value="1">Entrée</option>
        <option value="2">Sortie</option>
        <option value="3">Sortie externe</option>
        <option value="4">Retour</option>
      </select>
      <button @click="search" class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-700">
        Rechercher
      </button>
    </div>

    <!-- Tableau -->
    <div class="bg-white rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
        <span class="text-sm text-gray-500">{{ store.totalRecords }} enregistrement(s)</span>
        <div class="flex gap-2">
          <button @click="prevPage" :disabled="page === 1" class="px-3 py-1.5 text-sm border rounded-lg disabled:opacity-40">←</button>
          <span class="px-3 py-1.5 text-sm">Page {{ page }}</span>
          <button @click="nextPage" :disabled="page * pageSize >= store.totalRecords" class="px-3 py-1.5 text-sm border rounded-lg disabled:opacity-40">→</button>
        </div>
      </div>

      <div v-if="store.loading" class="py-12 text-center text-gray-400">Chargement...</div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Employé</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Département</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Date / Heure</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Type</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Lecteur</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in store.records" :key="r.id" class="border-t border-gray-50 hover:bg-gray-50">
            <td class="px-4 py-3">
              <div class="font-medium">{{ r.person_name }}</div>
              <div class="text-xs text-gray-400">{{ r.person_id }}</div>
            </td>
            <td class="px-4 py-3 text-gray-600">{{ r.dept_name }}</td>
            <td class="px-4 py-3 text-gray-600">{{ fmt(r.swipe_time) }}</td>
            <td class="px-4 py-3">
              <span :class="badgeClass(r.event_type)" class="px-2 py-1 rounded-full text-xs font-medium">
                {{ r.event_name }}
              </span>
            </td>
            <td class="px-4 py-3 text-gray-400 text-xs">{{ r.device_name }}</td>
          </tr>
          <tr v-if="store.records.length === 0">
            <td colspan="5" class="px-4 py-10 text-center text-gray-400">Aucun pointage trouvé.</td>
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

const store   = useAttendanceStore();
const page    = ref(1);
const pageSize = 50;

const filters = ref({
  startDate: dayjs().subtract(7, 'day').format('YYYY-MM-DD'),
  endDate:   dayjs().format('YYYY-MM-DD'),
  deptId:    '',
  eventType: '',
});

const fmt = t => t ? dayjs(t).format('DD/MM/YYYY HH:mm') : '—';

const badgeClass = t => ({
  '1': 'bg-green-100 text-green-700',
  '2': 'bg-red-100 text-red-700',
  '3': 'bg-orange-100 text-orange-700',
  '4': 'bg-blue-100 text-blue-700',
}[t] || 'bg-gray-100 text-gray-600');

async function search() {
  page.value = 1;
  await store.fetchRecords({ ...filters.value, page: 1, pageSize });
}

async function nextPage() {
  page.value++;
  await store.fetchRecords({ ...filters.value, page: page.value, pageSize });
}
async function prevPage() {
  if (page.value > 1) { page.value--; await store.fetchRecords({ ...filters.value, page: page.value, pageSize }); }
}

onMounted(async () => {
  await store.fetchDepartments();
  await search();
});
</script>
