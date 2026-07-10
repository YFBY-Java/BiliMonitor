<template>
  <div ref="chartRef" class="chart" :style="{ height: `${height}px` }"></div>
</template>

<script setup lang="ts">
import { LineChart, type LineSeriesOption } from 'echarts/charts'
import {
  GridComponent,
  type GridComponentOption,
  LegendComponent,
  type LegendComponentOption,
  TooltipComponent,
  type TooltipComponentOption
} from 'echarts/components'
import { init, use, type ComposeOption, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type TrendChartOption = ComposeOption<
  LineSeriesOption | GridComponentOption | TooltipComponentOption | LegendComponentOption
>

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

export interface TrendSeries {
  name: string
  values: number[]
  color?: string
}

interface TooltipParam {
  axisValue?: unknown
  axisValueLabel?: string
  marker?: string
  seriesName?: string
  value?: unknown
}

const props = defineProps<{
  labels: string[]
  values?: number[]
  series?: TrendSeries[]
  timestamps?: string[]
  height?: number
  compact?: boolean
  theme?: 'light' | 'dark'
  accentColor?: string
  valueFormat?: 'compact' | 'precise-compact'
}>()

const chartRef = ref<HTMLDivElement>()
let chart: ECharts | undefined

const height = computed(() => props.height ?? 280)
const isDark = computed(() => props.theme === 'dark')
const chartSeries = computed<TrendSeries[]>(() => {
  if (props.series?.length) return props.series
  return [{ name: '粉丝数', values: props.values ?? [], color: props.accentColor }]
})
const timeValues = computed(() => (props.timestamps ?? []).map((value) => Date.parse(value)))
const usesTimeAxis = computed(() => {
  const firstSeriesLength = chartSeries.value[0]?.values.length ?? 0
  return firstSeriesLength > 0 &&
    timeValues.value.length === firstSeriesLength &&
    timeValues.value.every((value) => Number.isFinite(value))
})

function trimFixed(value: number, digits: number) {
  return value.toFixed(digits).replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1')
}

function formatCompactNumber(value: number, axisSpan = 0) {
  const precise = props.valueFormat === 'precise-compact'
  if (Math.abs(value) >= 100000000) {
    const scaledSpan = axisSpan / 100000000
    const decimals = precise
      ? scaledSpan > 0 && scaledSpan < 0.1 ? 3 : scaledSpan > 0 && scaledSpan < 10 ? 2 : 1
      : axisSpan > 0 && axisSpan < 10000000 ? 2 : 1
    return `${trimFixed(value / 100000000, decimals)}亿`
  }
  if (Math.abs(value) >= 10000) {
    const scaledSpan = axisSpan / 10000
    const decimals = precise
      ? scaledSpan > 0 && scaledSpan < 0.1 ? 3 : scaledSpan > 0 && scaledSpan < 20 ? 2 : 1
      : scaledSpan > 0 && scaledSpan < 10 ? 2 : 1
    return `${trimFixed(value / 10000, decimals)}万`
  }
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(value)
}

function formatAxisNumber(value: number, axisSpan = 0) {
  return formatCompactNumber(value, axisSpan)
}

function formatTimePart(value: number, options: Intl.DateTimeFormatOptions) {
  return new Intl.DateTimeFormat('zh-CN', options).format(new Date(value))
}

function formatAxisTime(value: number, span: number) {
  if (!Number.isFinite(value)) return ''
  if (span > 86_400_000) {
    return formatTimePart(value, { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  }
  if (span < 120_000) {
    return formatTimePart(value, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  }
  return formatTimePart(value, { hour: '2-digit', minute: '2-digit' })
}

function formatTooltipTime(value: unknown) {
  const time = Number(value)
  if (!Number.isFinite(time)) return ''
  return formatTimePart(time, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function tooltipValue(value: unknown) {
  const candidate = Array.isArray(value) ? value[value.length - 1] : value
  const number = Number(candidate)
  return Number.isFinite(number) ? number : undefined
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function tooltipFormatter(params: unknown) {
  const items = (Array.isArray(params) ? params : [params]) as TooltipParam[]
  const title = escapeHtml(usesTimeAxis.value ? formatTooltipTime(items[0]?.axisValue) : String(items[0]?.axisValueLabel ?? ''))
  const rows = items
    .map((item) => {
      const value = tooltipValue(item.value)
      if (value == null) return ''
      return `
        <div style="display:flex;align-items:center;justify-content:space-between;gap:18px;min-width:150px;">
          <span style="display:inline-flex;align-items:center;gap:6px;color:inherit;">${item.marker ?? ''}${escapeHtml(item.seriesName || '粉丝数')}</span>
          <strong style="font-weight:800;">${formatCompactNumber(value, axisBounds().span)}</strong>
        </div>
      `
    })
    .join('')

  return `
    <div style="max-width:240px;white-space:nowrap;">
      <div style="margin-bottom:8px;font-weight:800;">${title}</div>
      ${rows}
    </div>
  `
}

function finiteValues() {
  return chartSeries.value.flatMap((item) => item.values).filter((value) => Number.isFinite(value))
}

function axisBounds() {
  const values = finiteValues()
  if (!values.length) return { span: 0 }

  const minValue = Math.min(...values)
  const maxValue = Math.max(...values)
  const center = (minValue + maxValue) / 2
  const observedRange = maxValue - minValue
  const magnitude = Math.max(Math.abs(center), Math.abs(minValue), Math.abs(maxValue), 1)
  const relativeFloor = magnitude * 0.000035
  const absoluteFloor = magnitude >= 1000000 ? 400 : magnitude >= 10000 ? 80 : 8
  const minimumSpan = Math.max(relativeFloor, absoluteFloor)
  const targetSpan = Math.max(observedRange * 2.2, minimumSpan)
  const paddedSpan = targetSpan * 1.18
  let min = center - paddedSpan / 2
  let max = center + paddedSpan / 2

  if (minValue >= 0 && min < 0) {
    min = 0
  }

  return {
    min: Math.floor(min),
    max: Math.ceil(max),
    span: Math.max(1, max - min)
  }
}

function timeAxisBounds() {
  if (!usesTimeAxis.value) return { span: 0 }
  const values = timeValues.value
  const minValue = Math.min(...values)
  const maxValue = Math.max(...values)
  const rawSpan = maxValue - minValue

  if (rawSpan <= 0) {
    const pad = 30 * 60 * 1000
    return { min: minValue - pad, max: maxValue + pad, span: pad * 2 }
  }

  const pad = Math.max(rawSpan * 0.04, rawSpan < 120_000 ? 30_000 : 60_000)
  return {
    min: minValue - pad,
    max: maxValue + pad,
    span: rawSpan + pad * 2
  }
}

function seriesData(values: number[]) {
  if (!usesTimeAxis.value) return values
  return values.map((value, index) => [timeValues.value[index], value])
}

function chartGrid(): GridComponentOption {
  if (props.compact) {
    return { left: 8, right: 8, top: 8, bottom: 8, containLabel: false }
  }
  return {
    left: 10,
    right: 24,
    top: chartSeries.value.length > 1 ? 36 : 14,
    bottom: 8,
    containLabel: true
  }
}

function renderChart() {
  if (!chartRef.value) return
  chart ??= init(chartRef.value)

  const textColor = isDark.value ? '#c8d5ee' : '#52627a'
  const axisColor = isDark.value ? '#355071' : '#dbe4f0'
  const splitColor = isDark.value ? 'rgba(148, 163, 184, 0.22)' : '#edf0f5'
  const tooltipBg = isDark.value ? 'rgba(15, 23, 42, 0.96)' : '#ffffff'
  const tooltipBorder = isDark.value ? '#2e4565' : '#d8e0ec'
  const axis = axisBounds()
  const timeAxis = timeAxisBounds()

  const option: TrendChartOption = {
    color: chartSeries.value.map((item) => item.color || props.accentColor || '#2f6df6'),
    grid: chartGrid(),
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: isDark.value ? '#f8fafc' : '#101828' },
      extraCssText: 'box-shadow: 0 10px 28px rgba(15, 23, 42, 0.14);',
      formatter: tooltipFormatter
    },
    legend: props.compact || chartSeries.value.length < 2
      ? undefined
      : { top: 0, right: 8, itemWidth: 10, itemHeight: 6, textStyle: { color: textColor } },
    xAxis: usesTimeAxis.value
      ? {
          type: 'time',
          min: timeAxis.min,
          max: timeAxis.max,
          boundaryGap: [0, 0],
          axisLabel: {
            show: !props.compact,
            color: textColor,
            hideOverlap: true,
            margin: 10,
            formatter: (value: number) => formatAxisTime(value, timeAxis.span)
          },
          axisTick: { show: !props.compact, lineStyle: { color: axisColor } },
          axisLine: { show: !props.compact, lineStyle: { color: axisColor } }
        }
      : {
          type: 'category',
          data: props.labels,
          boundaryGap: false,
          axisLabel: { show: !props.compact, color: textColor, hideOverlap: true, margin: 10 },
          axisTick: { show: !props.compact, lineStyle: { color: axisColor } },
          axisLine: { show: !props.compact, lineStyle: { color: axisColor } }
        },
    yAxis: {
      type: 'value',
      scale: true,
      min: axis.min,
      max: axis.max,
      minInterval: 1,
      axisLabel: {
        show: !props.compact,
        color: textColor,
        margin: 12,
        hideOverlap: true,
        formatter: (value: number) => formatAxisNumber(value, axis.span)
      },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: !props.compact, lineStyle: { color: splitColor } }
    },
    series: chartSeries.value.map((item) => ({
      name: item.name,
      type: 'line',
      smooth: true,
      symbol: props.compact ? 'none' : 'circle',
      symbolSize: props.compact ? 0 : 7,
      connectNulls: true,
      areaStyle: props.compact ? undefined : { opacity: isDark.value ? 0.18 : 0.12 },
      lineStyle: { width: props.compact ? 2 : 3 },
      data: seriesData(item.values)
    }))
  }
  chart.setOption(option, true)
  chart.resize()
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', renderChart)
})

watch(
  () => [props.labels, props.values, props.series, props.timestamps, props.compact, props.height, props.theme, props.accentColor, props.valueFormat],
  renderChart,
  { deep: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', renderChart)
  chart?.dispose()
})
</script>

<style scoped>
.chart {
  width: 100%;
  height: 280px;
}
</style>
