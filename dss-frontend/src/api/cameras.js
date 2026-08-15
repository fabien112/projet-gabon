import { api } from './http'

export async function fetchConfiguredCameras() {
  const { data } = await api.get('/cameras')
  return data
}

export async function discoverDssCameras() {
  const { data } = await api.get('/cameras/discover')
  return data
}

export async function addCamera({ channelId, name, site }) {
  const { data } = await api.post('/cameras', { channelId, name, site })
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
