import { createRouter, createWebHistory } from 'vue-router'
import ReportView from './views/ReportView.vue'
import SyncView from './views/SyncView.vue'
import LoginView from './views/LoginView.vue'
import { fetchMe } from './api/auth'
import { applyMe, clearSession, session } from './auth/session'

const CONFIG_PATHS = ['/config', '/sync', '/lea']

function isConfigPath(path) {
  return CONFIG_PATHS.some((p) => path === p || path.startsWith(`${p}/`))
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'report', component: ReportView },
    { path: '/config', name: 'config', component: SyncView, meta: { superAdmin: true } },
    { path: '/sync', redirect: '/config', meta: { superAdmin: true } },
    { path: '/lea', redirect: '/config', meta: { superAdmin: true } },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    try {
      const me = await fetchMe()
      if (me?.authenticated) {
        applyMe(me)
        return { path: '/' }
      }
    } catch {
      clearSession()
    }
    return true
  }

  try {
    const me = await fetchMe()
    if (me?.authenticated) {
      applyMe(me)
      const needsSuperAdmin = to.meta.superAdmin
        || to.matched.some((record) => record.meta.superAdmin)
        || isConfigPath(to.path)
      if (needsSuperAdmin && !session.superAdmin) {
        return { path: '/' }
      }
      return true
    }
  } catch {
    clearSession()
  }
  return { path: '/login', query: { redirect: isConfigPath(to.path) ? '/' : to.fullPath } }
})

export default router
