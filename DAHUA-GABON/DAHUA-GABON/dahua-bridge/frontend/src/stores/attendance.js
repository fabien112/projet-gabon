import { defineStore } from 'pinia';
import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

export const useAttendanceStore = defineStore('attendance', {
  state: () => ({
    stats:        null,
    records:      [],
    employees:    [],
    departments:  [],
    syncLog:      [],
    loading:      false,
    error:        null,
    totalRecords: 0,
    // Temps réel
    liveBadges:   [],   // badges reçus par SSE (max 50)
    sseConnected: false,
  }),

  actions: {
    // ── API REST ──────────────────────────────────────────────
    async fetchStats() {
      const { data } = await api.get('/attendance/stats');
      this.stats = data.data;
    },

    async fetchRecords(params = {}) {
      this.loading = true;
      try {
        const { data } = await api.get('/attendance', { params });
        this.records      = data.data;
        this.totalRecords = data.total;
      } finally {
        this.loading = false;
      }
    },

    async fetchEmployees(params = {}) {
      const { data } = await api.get('/employees', { params });
      this.employees = data.data;
    },

    async fetchDepartments() {
      const { data } = await api.get('/employees/departments');
      this.departments = data.data;
    },

    async fetchSyncLog() {
      const { data } = await api.get('/export/sync-log');
      this.syncLog = data.data;
    },

    async triggerSync() {
      this.loading = true;
      try {
        const { data } = await api.post('/export/sync');
        return data;
      } finally {
        this.loading = false;
      }
    },

    async exportSage(year, month) {
      const { data } = await api.post('/export/sage', null, { params: { year, month } });
      return data;
    },

    // ── SSE temps réel ────────────────────────────────────────
    addLiveBadges(badges) {
      this.liveBadges = [...badges, ...this.liveBadges].slice(0, 50);
      // Rafraîchir les stats silencieusement
      this.fetchStats().catch(() => {});
    },
  },
});
