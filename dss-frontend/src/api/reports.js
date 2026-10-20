import { api } from './http'

/** Sites sélectionnables (au moins une caméra active) : [{ id, name, cameras: [noms] }] */
export async function fetchReportSites() {
  const { data } = await api.get('/reports/sites')
  return data
}

export async function fetchReportStatus() {
  const { data } = await api.get('/reports/status')
  return data
}

export async function fetchPersonalizedReport(params) {
  const { site, sites, ...rest } = params
  // ids de sites sélectionnés : CSV unique ; chaque site agrège toutes ses caméras côté backend
  const selected = Array.isArray(sites)
    ? sites
    : Array.isArray(site)
      ? site
      : site != null && site !== ''
        ? String(site).split(',')
        : []
  const cleaned = [...new Set(selected.map((id) => String(id).trim()).filter(Boolean))]
  const siteParam =
    cleaned.length === 0 || cleaned.includes('all')
      ? 'all'
      : cleaned.join(',')

  const { data } = await api.get('/reports/personalized', {
    params: { ...rest, site: siteParam },
  })
  return data
}

/**
 * Abonnement SSE — reçoit un événement quand de nouvelles données sont écrites en base.
 * @returns {() => void} fonction pour fermer la connexion
 */
export function subscribeReportDataChanges({ onDataChanged, onError } = {}) {
  const es = new EventSource('/api/reports/events', { withCredentials: true })

  es.addEventListener('data-changed', (event) => {
    try {
      onDataChanged?.(JSON.parse(event.data))
    } catch (e) {
      onError?.(e)
    }
  })

  es.onerror = () => {
    onError?.(new Error('Connexion temps réel interrompue'))
  }

  return () => es.close()
}
