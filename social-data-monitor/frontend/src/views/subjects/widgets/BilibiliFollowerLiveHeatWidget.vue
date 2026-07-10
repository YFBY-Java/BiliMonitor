<template>
  <MonitorWidgetShell
    :title="`${workbench.subject.displayName} 粉丝数与直播热度监控`"
    subtitle="复用 B站粉丝快照与直播间快照，按时间桶对齐"
    badge="双指标"
    badge-type="primary"
    size="large"
  >
    <div class="dual-legend">
      <span><i class="follower"></i>粉丝数</span>
      <span><i class="heat"></i>直播间热度</span>
      <el-tag effect="light" round>按时间对齐</el-tag>
    </div>
    <div v-if="hasPoints" ref="chartRef" class="dual-chart"></div>
    <el-empty v-else class="empty-chart" description="暂无趋势点，等待粉丝或直播采集快照" />
    <div class="split-stats">
      <div>
        <span>当前粉丝</span>
        <strong>{{ formatCompactNumber(workbench.summary.followerCount) }}</strong>
      </div>
      <div>
        <span>24h 净增</span>
        <strong :class="{ up: (workbench.summary.followerDelta24h ?? 0) > 0, down: (workbench.summary.followerDelta24h ?? 0) < 0 }">
          {{ formatSigned(workbench.summary.followerDelta24h) }}
        </strong>
      </div>
      <div>
        <span>当前热度</span>
        <strong>{{ formatCompactNumber(workbench.summary.onlineCount) }}</strong>
      </div>
      <div>
        <span>热度峰值</span>
        <strong>{{ formatCompactNumber(workbench.summary.onlinePeak24h) }}</strong>
      </div>
    </div>
  </MonitorWidgetShell>
</template>

<script setup lang="ts">
import { LineChart, type LineSeriesOption } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  type GridComponentOption,
  type LegendComponentOption,
  type TooltipComponentOption
} from 'echarts/components'
import { init, use, type ComposeOption, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { SubjectTrend, SubjectWorkbench } from '@/api/subjects'
import MonitorWidgetShell from '../components/MonitorWidgetShell.vue'
import { formatCompactNumber, formatSigned } from '../formatters'

type ChartOption = ComposeOption<LineSeriesOption | GridComponentOption | TooltipComponentOption | LegendComponentOption>

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  workbench: SubjectWorkbench
  trend?: SubjectTrend
}>()

const chartRef = ref<HTMLDivElement>()
let chart: ECharts | undefined

const points = computed(() => props.trend?.points ?? [])
const hasPoints = computed(() => points.value.length > 0)

function formatTime(value: number) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function chartData(key: 'followerCount' | 'liveOnlineCount') {
  return points.value
    .map((point) => {
      const time = Date.parse(point.bucketAt)
      const value = point[key]
      return Number.isFinite(time) && value != null ? [time, value] : undefined
    })
    .filter((item): item is number[] => item != null)
}

function axisBounds(data: number[][], metric: 'follower' | 'heat') {
  const values = data.map((item) => item[1]).filter((value) => Number.isFinite(value))
  if (!values.length) return undefined
  const minValue = Math.min(...values)
  const maxValue = Math.max(...values)
  const magnitude = Math.max(Math.abs(maxValue), Math.abs(minValue), 1)
  const rawSpan = Math.max(0, maxValue - minValue)
  const followerMagnitudeFloor = Math.max(60, Math.min(2_400, magnitude * 0.00003))
  const followerChangeFloor = rawSpan > 0 ? rawSpan * 8 : followerMagnitudeFloor
  const heatMagnitudeFloor = Math.max(80, Math.min(30_000, magnitude * 0.01))
  const heatChangeFloor = rawSpan > 0 ? rawSpan * 1.95 : heatMagnitudeFloor
  const minSpan = metric === 'follower'
    ? Math.max(followerMagnitudeFloor, followerChangeFloor)
    : Math.max(heatMagnitudeFloor, heatChangeFloor)
  const visibleSpan = Math.max(rawSpan, minSpan)
  const paddingRatio = metric === 'follower' ? 0.08 : 0.1
  const padding = Math.max(visibleSpan * paddingRatio, metric === 'follower' ? 6 : 8)
  const center = (minValue + maxValue) / 2
  return {
    min: Math.max(0, Math.floor(center - visibleSpan / 2 - padding)),
    max: Math.ceil(center + visibleSpan / 2 + padding)
  }
}

function formatAxisNumber(value: number) {
  const absolute = Math.abs(value)
  if (absolute >= 100_000_000) {
    return `${(value / 100_000_000).toFixed(2).replace(/\.?0+$/, '')}亿`
  }
  if (absolute >= 10_000) {
    return `${(value / 10_000).toFixed(2).replace(/\.?0+$/, '')}万`
  }
  return formatCompactNumber(value)
}

function timeBounds(data: number[][]) {
  const times = data.map((item) => item[0]).filter((value) => Number.isFinite(value))
  if (!times.length) return undefined
  const minTime = Math.min(...times)
  const maxTime = Math.max(...times)
  if (minTime === maxTime) {
    return {
      min: minTime - 15 * 60 * 1000,
      max: maxTime + 15 * 60 * 1000
    }
  }
  const span = maxTime - minTime
  const padding = Math.min(Math.max(span * 0.04, 60 * 1000), 60 * 60 * 1000)
  return {
    min: minTime - padding,
    max: maxTime + padding
  }
}

function renderChart() {
  if (!chartRef.value || !hasPoints.value) return
  chart ??= init(chartRef.value)
  const followerData = chartData('followerCount')
  const heatData = chartData('liveOnlineCount')
  const followerAxis = axisBounds(followerData, 'follower')
  const heatAxis = axisBounds(heatData, 'heat')
  const timeAxis = timeBounds([...followerData, ...heatData])

  const option: ChartOption = {
    color: ['#2f6df6', '#ef5b7d'],
    grid: { left: 12, right: 18, top: 28, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: '#fff',
      borderColor: '#dbe4f0',
      textStyle: { color: '#0f172a' },
      formatter: (params: unknown) => {
        const items = Array.isArray(params) ? params : [params]
        const first = items[0] as { axisValue?: number } | undefined
        const title = first?.axisValue ? formatTime(first.axisValue) : ''
        const rows = items.map((item) => {
          const typed = item as { marker?: string; seriesName?: string; value?: [number, number] }
          const value = Array.isArray(typed.value) ? typed.value[1] : undefined
          return `<div style="display:flex;justify-content:space-between;gap:18px;min-width:160px;">
            <span>${typed.marker ?? ''}${typed.seriesName ?? ''}</span>
            <strong>${formatCompactNumber(value)}</strong>
          </div>`
        }).join('')
        return `<div><div style="font-weight:800;margin-bottom:8px;">${title}</div>${rows}</div>`
      }
    },
    xAxis: {
      type: 'time',
      min: timeAxis?.min,
      max: timeAxis?.max,
      axisLine: { lineStyle: { color: '#dbe4f0' } },
      axisTick: { lineStyle: { color: '#dbe4f0' } },
      axisLabel: { color: '#667085', hideOverlap: true, formatter: (value: number) => formatTime(value) }
    },
    yAxis: [
      {
        type: 'value',
        scale: true,
        min: followerAxis?.min,
        max: followerAxis?.max,
        name: '粉丝',
        nameTextStyle: { color: '#667085' },
        axisLabel: { color: '#667085', formatter: (value: number) => formatAxisNumber(value) },
        splitLine: { lineStyle: { color: '#edf1f6' } }
      },
      {
        type: 'value',
        scale: true,
        min: heatAxis?.min,
        max: heatAxis?.max,
        name: '热度',
        nameTextStyle: { color: '#667085' },
        axisLabel: { color: '#667085', formatter: (value: number) => formatAxisNumber(value) },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '粉丝数',
        type: 'line',
        smooth: 0.18,
        yAxisIndex: 0,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2.4 },
        areaStyle: { opacity: 0.06 },
        connectNulls: true,
        data: followerData
      },
      {
        name: '直播间热度',
        type: 'line',
        smooth: 0.22,
        yAxisIndex: 1,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.08 },
        connectNulls: true,
        data: heatData
      }
    ]
  }

  chart.setOption(option, true)
  chart.resize()
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', renderChart)
})

watch(() => props.trend, renderChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', renderChart)
  chart?.dispose()
})
</script>

<style scoped>
:deep(.monitor-widget__body) {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.dual-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  color: #475467;
  font-size: 12px;
}

.dual-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.dual-legend i {
  width: 18px;
  height: 3px;
  border-radius: 999px;
}

.dual-legend .follower {
  background: #2f6df6;
}

.dual-legend .heat {
  background: linear-gradient(90deg, #ef5b7d, #f59e0b);
}

.dual-chart {
  width: 100%;
  flex: 1;
  min-height: 330px;
  border-radius: 8px;
  background: linear-gradient(90deg, rgba(47, 109, 246, 0.06), rgba(245, 158, 11, 0.06));
}

.empty-chart {
  flex: 1;
  min-height: 330px;
  border-radius: 8px;
  background: #f8fafc;
}

.split-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.split-stats div {
  min-width: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
}

.split-stats span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.split-stats strong {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
  font-size: 20px;
  font-weight: 900;
}

.split-stats .up {
  color: #047857;
}

.split-stats .down {
  color: #be123c;
}

@media (max-width: 1180px) {
  .split-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
