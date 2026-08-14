import { createRouter, createWebHistory } from 'vue-router'
import ReportView from './views/ReportView.vue'
import ConfigView from './views/ConfigView.vue'
import LoginView from './views/LoginView.vue'
import { fetchMe } from './api/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'report', component: ReportView },
    { path: '/config', name: 'config', component: ConfigView },
    { path: '/sync', redirect: '/config' },
    { path: '/lea', redirect: '/config' },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    try {
      const me = await fetchMe()
      if (me?.authenticated) return { path: '/' }
    } catch {
      // reste sur login
    }
    return true
  }

  try {
    const me = await fetchMe()
    if (me?.authenticated) return true
  } catch {
    // redirect login
  }
  return { path: '/login', query: { redirect: to.fullPath } }
})

export default router
