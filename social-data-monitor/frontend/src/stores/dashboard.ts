import { defineStore } from 'pinia'
import { fetchSystemOverview, type SystemOverview } from '@/api/dashboard'

export const useDashboardStore = defineStore('dashboard', {
  state: () => ({
    overview: null as SystemOverview | null,
    loading: false
  }),
  actions: {
    async loadOverview() {
      this.loading = true
      try {
        this.overview = await fetchSystemOverview()
      } finally {
        this.loading = false
      }
    }
  }
})

