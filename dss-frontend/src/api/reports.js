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
  const { data } = await api.get('/reports/personalized', { params })
  return data
}
