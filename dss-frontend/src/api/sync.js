import { api } from './http'

export async function fetchSyncStatus() {
  const { data } = await api.get('/sync/status')
  return data
}

/**
 * Abonnement SSE — le serveur pousse le statut à chaque changement sync/poll.
 * @returns {() => void} fonction pour fermer la connexion
 */
export function subscribeSyncStatus({ onStatus, onError } = {}) {
  const es = new EventSource('/api/sync/events', { withCredentials: true })

  es.addEventListener('status', (event) => {
    try {
      onStatus?.(JSON.parse(event.data))
    } catch (e) {
      onError?.(e)
    }
  })

  es.onerror = () => {
    onError?.(new Error('Connexion temps réel interrompue'))
  }

  return () => es.close()
}

export async function startMissingDataSync() {
  const { data } = await api.post('/sync/missing')
  return data
}

export async function startHistorySync({ from, to }) {
  const { data } = await api.post('/sync/history', null, {
    params: { from, to },
  })
  return data
}

export async function compareDssDb({ from, to }) {
  const { data } = await api.get('/sync/compare', {
    params: { from, to },
    timeout: 180000,
  })
  return data
}

export async function reconnectDss() {
  const { data } = await api.post('/sync/dss-reconnect')
  return data
}
