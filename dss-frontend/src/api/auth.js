import { api } from './http'

export async function loginApp({ username, password }) {
  const { data } = await api.post('/app/login', { username, password })
  return data
}

export async function logoutApp() {
  const { data } = await api.post('/app/logout')
  return data
}

export async function fetchMe() {
  const { data } = await api.get('/app/me')
  return data
}
