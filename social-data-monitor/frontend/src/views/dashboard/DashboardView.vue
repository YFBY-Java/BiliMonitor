<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Dashboard</h1>
        <p class="page-subtitle">Bilibili MVP 采集健康、任务状态和趋势概览。</p>
      </div>
      <el-button :loading="store.loading" type="primary" @click="store.loadOverview()">刷新</el-button>
    </div>

    <div class="metric-grid">
      <el-card class="metric-card" shadow="never">
        <div class="metric-label">平台数量</div>
        <div class="metric-value">{{ overview.platformCount }}</div>
      </el-card>
      <el-card class="metric-card" shadow="never">
        <div class="metric-label">启用任务</div>
        <div class="metric-value">{{ overview.enabledTaskCount }}</div>
      </el-card>
      <el-card class="metric-card" shadow="never">
        <div class="metric-label">今日成功</div>
        <div class="metric-value">{{ overview.todaySuccessCount }}</div>
      </el-card>
      <el-card class="metric-card" shadow="never">
        <div class="metric-label">今日失败</div>
        <div class="metric-value">{{ overview.todayFailedCount }}</div>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>UP 主粉丝趋势 Mock</template>
      <TrendChart :labels="chartLabels" :values="chartValues" />
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import TrendChart from '@/components/charts/TrendChart.vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()

const overview = computed(() => store.overview ?? {
  platformCount: 0,
  enabledTaskCount: 0,
  todaySuccessCount: 0,
  todayFailedCount: 0,
  followerTrend: []
})

const chartLabels = computed(() => overview.value.followerTrend.map((item) => item.date))
const chartValues = computed(() => overview.value.followerTrend.map((item) => item.value))

onMounted(() => {
  store.loadOverview()
})
</script>

