<template>
  <div class="p-6">
    <h2 class="text-2xl font-bold text-gray-800 mb-6">Employés</h2>

    <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-4 mb-5 flex gap-3">
      <input v-model="search" @input="doSearch" type="text" placeholder="Rechercher un employé..."
        class="flex-1 border border-gray-200 rounded-lg px-3 py-2 text-sm" />
      <select v-model="deptId" @change="doSearch" class="border border-gray-200 rounded-lg px-3 py-2 text-sm">
        <option value="">Tous les départements</option>
        <option v-for="d in store.departments" :key="d.org_code" :value="d.org_code">{{ d.org_name }}</option>
      </select>
    </div>

    <div class="bg-white rounded-xl shadow-sm border border-gray-100">
      <div class="px-5 py-4 border-b border-gray-100 text-sm text-gray-500">
        {{ store.employees.length }} employé(s)
      </div>
      <table class="w-full text-sm">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Nom</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Matricule</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Département</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Poste</th>
            <th class="px-4 py-3 text-left text-gray-500 font-medium">Carte</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in store.employees" :key="e.person_id" class="border-t border-gray-50 hover:bg-gray-50">
            <td class="px-4 py-3 font-medium">{{ e.first_name }} {{ e.last_name }}</td>
            <td class="px-4 py-3 text-gray-500 text-xs">{{ e.person_id }}</td>
            <td class="px-4 py-3 text-gray-600">{{ e.org_name }}</td>
            <td class="px-4 py-3 text-gray-500">{{ e.job_title || '—' }}</td>
            <td class="px-4 py-3 text-gray-400 text-xs">{{ e.card_no || '—' }}</td>
          </tr>
          <tr v-if="store.employees.length === 0">
            <td colspan="5" class="px-4 py-10 text-center text-gray-400">
              Aucun employé. Ajouter des personnes dans DSS Client.
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAttendanceStore } from '../stores/attendance';

const store  = useAttendanceStore();
const search = ref('');
const deptId = ref('');

let timer;
function doSearch() {
  clearTimeout(timer);
  timer = setTimeout(() => {
    store.fetchEmployees({ search: search.value, deptId: deptId.value });
  }, 300);
}

onMounted(async () => {
  await store.fetchDepartments();
  await store.fetchEmployees();
});
</script>
