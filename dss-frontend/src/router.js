import { createRouter, createWebHistory } from 'vue-router'
import ReportView from './views/ReportView.vue'
import LeaSyncView from './views/LeaSyncView.vue'
import LoginView from './views/LoginView.vue'
import { fetchMe } from './api/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'report', component: ReportView },
    { path: '/lea', name: 'lea', component: LeaSyncView },
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
