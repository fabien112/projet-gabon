<template>
  <div class="min-h-screen flex">
    <!-- Sidebar -->
    <aside class="w-64 bg-gray-900 text-white flex flex-col">
      <div class="px-6 py-5 border-b border-gray-700">
        <h1 class="text-lg font-bold text-white">Dahua Bridge</h1>
        <p class="text-xs text-gray-400 mt-1">Gestion des Présences</p>
      </div>

      <nav class="flex-1 px-4 py-4 space-y-1">
        <router-link
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors"
          :class="$route.path === item.to
            ? 'bg-blue-600 text-white'
            : 'text-gray-300 hover:bg-gray-700'"
        >
          <span class="text-lg">{{ item.icon }}</span>
          {{ item.label }}
        </router-link>
      </nav>

      <div class="px-4 py-4 border-t border-gray-700 space-y-2">
        <!-- Statut DSS -->
        <div class="flex items-center gap-2 text-xs text-gray-400">
          <span :class="serverOk ? 'text-green-400' : 'text-red-400'">●</span>
          {{ serverOk ? 'DSS Connecté' : 'DSS Déconnecté' }}
        </div>
        <!-- Statut flux temps réel -->
        <div class="flex items-center gap-2 text-xs text-gray-400">
          <span class="relative flex h-2 w-2">
            <span v-if="store.sseConnected"
              class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
            <span :class="store.sseConnected ? 'bg-green-400' : 'bg-gray-500'"
              class="relative inline-flex rounded-full h-2 w-2"></span>
          </span>
          {{ store.sseConnected ? 'Temps réel actif' : 'Temps réel off' }}
        </div>
      </div>
    </aside>

    <!-- Contenu -->
    <main class="flex-1 overflow-auto">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import axios from 'axios';
import { useAttendanceStore } from './stores/attendance';

const store     = useAttendanceStore();
const serverOk  = ref(false);

const nav = [
  { to: '/',              icon: '📊', label: 'Tableau de bord' },
  { to: '/pointages',     icon: '🕐', label: 'Pointages'       },
  { to: '/employes',      icon: '👥', label: 'Employés'        },
  { to: '/anomalies',     icon: '⚠️',  label: 'Anomalies'       },
  { to: '/export',        icon: '📤', label: 'Export Sage X3'  },
  { to: '/configuration', icon: '⚙️',  label: 'Configuration'   },
];

// Vérification périodique du serveur
let statusTimer = null;
async function checkStatus() {
  try {
    await axios.get('/api/status');
    serverOk.value = true;
  } catch {
    serverOk.value = false;
  }
}

// SSE — démarré une seule fois au niveau de l'app
let es = null;
let reconnectTimer = null;

function connectSSE() {
  if (es) es.close();
  es = new EventSource('/api/events');

  es.addEventListener('badge', (e) => {
    const badges = JSON.parse(e.data);
    store.addLiveBadges(badges);
  });

  es.onopen = () => {
    store.sseConnected = true;
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
  };

  es.onerror = () => {
    store.sseConnected = false;
    es.close();
    reconnectTimer = setTimeout(connectSSE, 5000);
  };
}

onMounted(() => {
  checkStatus();
  statusTimer = setInterval(checkStatus, 30000);
  connectSSE();
});

onUnmounted(() => {
  clearInterval(statusTimer);
  if (es)             es.close();
  if (reconnectTimer) clearTimeout(reconnectTimer);
});
</script>
