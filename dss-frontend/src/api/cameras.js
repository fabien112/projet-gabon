import { api } from './http'

export async function fetchConfiguredCameras() {
  const { data } = await api.get('/cameras')
  return data
}

export async function discoverDssCameras() {
  const { data } = await api.get('/cameras/discover')
  return data
}

export async function addCamera({ channelId, name, siteId }) {
  const { data } = await api.post('/cameras', { channelId, name, siteId })
  return data
}

export async function updateCamera(id, payload) {
  const { data } = await api.patch(`/cameras/${id}`, payload)
  return data
}

export async function removeCamera(id) {
  const { data } = await api.delete(`/cameras/${id}`)
  return data
}

/** Sites configurés : [{ id, name, cameraCount, cameras: [noms] }] */
export async function fetchSites() {
  const { data } = await api.get('/sites')
  return data
}

export async function createSite(name) {
  const { data } = await api.post('/sites', { name })
  return data
}

export async function updateSite(id, name) {
  const { data } = await api.patch(`/sites/${id}`, { name })
  return data
}

export async function deleteSite(id) {
  const { data } = await api.delete(`/sites/${id}`)
  return data
}
