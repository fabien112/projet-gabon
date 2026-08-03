import { createRouter, createWebHistory } from 'vue-router'
import ReportView from './views/ReportView.vue'
import LeaSyncView from './views/LeaSyncView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'report', component: ReportView },
    { path: '/lea', name: 'lea', component: LeaSyncView },
  ],
})

export default router
