import { ref, onUnmounted } from 'vue';

/**
 * Composable SSE — s'abonne à /api/events et expose les badges en temps réel.
 * Reconnexion automatique si la connexion est perdue.
 */
export function useSSE() {
  const liveBadges   = ref([]);   // badges reçus par SSE
  const sseConnected = ref(false);
  let   es           = null;
  let   reconnectTimer = null;

  function connect() {
    if (es) es.close();

    es = new EventSource('/api/events');

    es.addEventListener('badge', (e) => {
      const badges = JSON.parse(e.data);
      // Ajouter en tête de liste, garder 50 max
      liveBadges.value = [...badges, ...liveBadges.value].slice(0, 50);
      sseConnected.value = true;
    });

    es.onopen = () => {
      sseConnected.value = true;
      if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
    };

    es.onerror = () => {
      sseConnected.value = false;
      es.close();
      // Reconnexion dans 5 secondes
      reconnectTimer = setTimeout(connect, 5000);
    };
  }

  connect();

  onUnmounted(() => {
    if (es)            es.close();
    if (reconnectTimer) clearTimeout(reconnectTimer);
  });

  return { liveBadges, sseConnected };
}
