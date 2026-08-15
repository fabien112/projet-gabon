import { api } from './http'

export async function fetchAppUsers() {
  const { data } = await api.get('/app/users')
  return data
}

export async function createAppUser({ username, password }) {
  const { data } = await api.post('/app/users', { username, password })
  return data
}

export async function updateAppUser(id, payload) {
  const { data } = await api.patch(`/app/users/${id}`, payload)
  return data
}

export async function deleteAppUser(id) {
  const { data } = await api.delete(`/app/users/${id}`)
  return data
}
