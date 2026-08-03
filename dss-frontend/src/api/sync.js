import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

export async function fetchSyncStatus() {
  const { data } = await api.get('/sync/status')
  return data
}

export async function startHistorySync({ from, to }) {
  const { data } = await api.post('/sync/history', null, {
    params: { from, to },
  })
  return data
}
