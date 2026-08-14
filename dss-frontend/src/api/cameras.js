import { api } from './http'

export async function fetchConfiguredCameras() {
  const { data } = await api.get('/cameras')
  return data
}

export async function fetchAvailableCameras() {
  const { data } = await api.get('/cameras/available')
  return data
}

export async function addCamera(channelId) {
  const { data } = await api.post('/cameras', { channelId })
  return data
}

export async function removeCamera(id) {
  const { data } = await api.delete(`/cameras/${id}`)
  return data
}
