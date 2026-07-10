<template>
  <section ref="boardRef" class="widget-board" :class="{ 'is-resizing': resizing }" :style="boardStyle">
    <div class="board-main board-primary board-resizable">
      <BilibiliFollowerLiveHeatWidget :workbench="workbench" :trend="trend" />
      <button class="resize-handle" type="button" title="拖动调整趋势图大小" @pointerdown="startResize">
        <span></span>
      </button>
    </div>
    <div class="board-side board-primary">
      <BilibiliLiveDanmuWidget :workbench="workbench" @refresh="emit('refresh')" />
    </div>
    <div class="board-main">
      <SubjectHealthEventWidget :workbench="workbench" />
    </div>
    <div class="board-side">
      <MonitorWidgetShell title="待添加模块" badge="可扩展" badge-type="info">
        <div class="empty-slot">
          <el-tag effect="light" round>Widget Slot</el-tag>
          <strong>内容表现 / 事件流 / AI 摘要</strong>
          <p>后续可接可编排布局，把更多用户维度放到这里。</p>
        </div>
      </MonitorWidgetShell>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import type { SubjectTrend, SubjectWorkbench } from '@/api/subjects'
import MonitorWidgetShell from './MonitorWidgetShell.vue'
import BilibiliFollowerLiveHeatWidget from '../widgets/BilibiliFollowerLiveHeatWidget.vue'
import BilibiliLiveDanmuWidget from '../widgets/BilibiliLiveDanmuWidget.vue'
import SubjectHealthEventWidget from '../widgets/SubjectHealthEventWidget.vue'

defineProps<{
  workbench: SubjectWorkbench
  trend?: SubjectTrend
}>()

const emit = defineEmits<{
  refresh: []
}>()

const boardRef = ref<HTMLElement>()
const mainWidthPercent = ref(61)
const primaryHeight = ref(650)
const resizing = ref(false)
let resizeFrame = 0
let resizeState:
  | {
      startX: number
      startY: number
      boardWidth: number
      startMainWidth: number
      startHeight: number
    }
  | undefined

const boardStyle = computed(() => ({
  '--subject-main-width': `${mainWidthPercent.value}%`,
  '--subject-widget-height': `${primaryHeight.value}px`
}))

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function requestChartResize() {
  if (resizeFrame) return
  resizeFrame = window.requestAnimationFrame(() => {
    resizeFrame = 0
    window.dispatchEvent(new Event('resize'))
  })
}

function startResize(event: PointerEvent) {
  const board = boardRef.value
  const mainCard = (event.currentTarget as HTMLElement).closest('.board-main')
  if (!board || !mainCard) return

  event.preventDefault()
  resizing.value = true
  const boardRect = board.getBoundingClientRect()
  const mainRect = mainCard.getBoundingClientRect()
  resizeState = {
    startX: event.clientX,
    startY: event.clientY,
    boardWidth: boardRect.width,
    startMainWidth: mainRect.width,
    startHeight: primaryHeight.value
  }
  document.body.style.cursor = 'nwse-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('pointermove', handleResize)
  window.addEventListener('pointerup', stopResize, { once: true })
}

function handleResize(event: PointerEvent) {
  if (!resizeState) return
  const minSideWidth = resizeState.boardWidth >= 960 ? 360 : 0
  const minMainWidth = resizeState.boardWidth >= 960 ? 520 : 320
  const nextMainWidth = clamp(
    resizeState.startMainWidth + event.clientX - resizeState.startX,
    minMainWidth,
    Math.max(minMainWidth, resizeState.boardWidth - minSideWidth - 18)
  )
  mainWidthPercent.value = clamp((nextMainWidth / resizeState.boardWidth) * 100, 44, 76)
  primaryHeight.value = clamp(resizeState.startHeight + event.clientY - resizeState.startY, 560, 920)
  requestChartResize()
}

function stopResize() {
  resizing.value = false
  resizeState = undefined
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('pointermove', handleResize)
  requestChartResize()
}

onBeforeUnmount(() => {
  if (resizeFrame) {
    window.cancelAnimationFrame(resizeFrame)
  }
  stopResize()
})
</script>

<style scoped>
.widget-board {
  display: grid;
  grid-template-columns: minmax(520px, var(--subject-main-width)) minmax(360px, 1fr);
  gap: 14px;
  align-items: stretch;
}

.board-main,
.board-side {
  min-width: 0;
  display: flex;
}

.board-primary {
  height: var(--subject-widget-height);
}

.board-resizable {
  position: relative;
}

.board-main > *,
.board-side > * {
  width: 100%;
}

.resize-handle {
  position: absolute;
  right: 9px;
  bottom: 9px;
  width: 28px;
  height: 28px;
  z-index: 3;
  display: grid;
  place-items: center;
  border: 1px solid #d7e2f0;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
  cursor: nwse-resize;
  opacity: 0.7;
  transition: opacity 0.18s ease, transform 0.18s ease, border-color 0.18s ease;
}

.resize-handle:hover,
.widget-board.is-resizing .resize-handle {
  opacity: 1;
  transform: translate(-1px, -1px);
  border-color: #8cb7ff;
}

.resize-handle span {
  width: 13px;
  height: 13px;
  border-right: 2px solid #7b8ba3;
  border-bottom: 2px solid #7b8ba3;
  background:
    linear-gradient(135deg, transparent 0 45%, #7b8ba3 46% 54%, transparent 55%),
    linear-gradient(135deg, transparent 0 68%, #9aa8bb 69% 76%, transparent 77%);
}

.empty-slot {
  min-height: 214px;
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 18px;
  border: 1px dashed #b8c5d6;
  border-radius: 8px;
  background: repeating-linear-gradient(135deg, #f8fafc 0, #f8fafc 12px, #f2f6fb 12px, #f2f6fb 24px);
  color: #64748b;
  text-align: center;
}

.empty-slot strong {
  color: #0f172a;
  font-size: 16px;
}

.empty-slot p {
  max-width: 280px;
  margin: 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 1180px) {
  .widget-board {
    grid-template-columns: 1fr;
  }

  .board-primary {
    height: auto;
    min-height: var(--subject-widget-height);
  }

  .resize-handle {
    display: none;
  }
}
</style>
