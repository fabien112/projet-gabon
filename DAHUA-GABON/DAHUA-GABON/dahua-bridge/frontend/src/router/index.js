import { createRouter, createWebHashHistory } from 'vue-router';

const routes = [
  { path: '/',             name: 'Dashboard',     component: () => import('../views/Dashboard.vue') },
  { path: '/pointages',    name: 'Attendance',    component: () => import('../views/Attendance.vue') },
  { path: '/employes',     name: 'Employees',     component: () => import('../views/Employees.vue') },
  { path: '/anomalies',    name: 'Anomalies',     component: () => import('../views/Anomalies.vue') },
  { path: '/export',       name: 'Export',        component: () => import('../views/Export.vue') },
  { path: '/configuration',name: 'Configuration', component: () => import('../views/Configuration.vue') },
];

export default createRouter({ history: createWebHashHistory(), routes });
