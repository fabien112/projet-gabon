import { api } from './http'

export async function fetchCameras() {
  const { data } = await api.get('/reports/cameras')
  return data
}

export async function fetchReportStatus() {
  const { data } = await api.get('/reports/status')
  return data
}

export async function fetchPersonalizedReport(params) {
  const { camera, cameras, ...rest } = params
  // channelIds sélectionnés : CSV unique pour un filtre IN exact côté backend
  const selected = Array.isArray(cameras)
    ? cameras
    : Array.isArray(camera)
      ? camera
      : typeof camera === 'string' && camera
        ? camera.split(',')
        : []
  const cleaned = [...new Set(selected.map((id) => String(id).trim()).filter(Boolean))]
  const cameraParam =
    cleaned.length === 0 || cleaned.includes('all')
      ? 'all'
      : cleaned.join(',')

  const { data } = await api.get('/reports/personalized', {
    params: { ...rest, camera: cameraParam },
  })
  return data
}
