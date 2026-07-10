<template>
  <section class="page bilibili-page" :class="themeClass">
    <div class="page-header monitor-header">
      <div>
        <h1 class="page-title">B站粉丝趋势监控</h1>
        <p class="page-subtitle">横向用户块展示采集节奏，点击用户查看最多 4 条趋势。</p>
      </div>
    </div>

    <BilibiliAuthPanel />

    <section class="control-surface">
      <el-form class="add-form" :inline="true" @submit.prevent>
        <el-form-item label="UID">
          <el-input
            v-model="addForm.mid"
            class="mid-input"
            inputmode="numeric"
            placeholder="例如 2"
            clearable
            @keyup.enter="submitAdd"
          />
        </el-form-item>
        <el-form-item label="间隔">
          <el-input-number
            v-model="addForm.intervalSeconds"
            :min="MIN_INTERVAL_SECONDS"
            :max="MAX_INTERVAL_SECONDS"
            :step="intervalStep(addForm.intervalSeconds)"
            :controls="false"
            class="interval-input"
          />
          <span class="unit">秒</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Plus" :loading="adding" @click="submitAdd">添加监控</el-button>
        </el-form-item>
      </el-form>

      <div class="control-actions">
        <div class="theme-toggle" :class="{ dark: isDarkTheme }">
          <span class="theme-label">主题</span>
          <span>浅色</span>
          <el-switch
            v-model="isDarkTheme"
            inline-prompt
            :active-icon="Moon"
            :inactive-icon="Sunny"
            aria-label="切换深色模式"
          />
          <span>深色</span>
        </div>
        <el-button :icon="Refresh" :loading="loading" @click="() => loadUsers()">刷新</el-button>
        <div class="toolbar-meta">
          <span>{{ users.length }} 个用户</span>
          <span>{{ activeCount }} 个启用</span>
          <span>{{ selectedTrendUsers.length }} 个趋势</span>
          <span>{{ errorCount }} 个异常</span>
        </div>
      </div>
    </section>

    <el-alert
      v-if="shortIntervalNotice"
      class="notice-alert"
      type="warning"
      title="已允许 1 秒采集间隔；短间隔会受全局请求节流和失败退避保护，建议只用于少量用户的临时观察。"
      show-icon
      :closable="false"
    />

    <section class="summary-grid">
      <article class="summary-item">
        <span>总粉丝</span>
        <strong>{{ formatCount(totalFollowers) }}</strong>
      </article>
      <article class="summary-item">
        <span>最近净增</span>
        <strong :class="deltaClass(totalDelta)">{{ formatDelta(totalDelta) }}</strong>
      </article>
      <article class="summary-item">
        <span>下次采集</span>
        <strong>{{ nextCollectText }}</strong>
      </article>
      <article class="summary-item">
        <span>采集间隔</span>
        <strong>{{ intervalText }}</strong>
      </article>
    </section>

    <el-empty v-if="!loading && users.length === 0" description="暂无监控用户">
      <el-button type="primary" :icon="Plus" @click="focusMidInput">添加 UID</el-button>
    </el-empty>

    <template v-else>
      <section class="monitor-strip-section">
        <div class="section-heading">
          <div>
            <h2>监控用户</h2>
            <p>横向卡片自适应宽度，最多选择 4 个进入趋势图表。</p>
          </div>
          <span>{{ selectedTrendUsers.length }}/{{ MAX_SELECTED_TRENDS }} 已选</span>
        </div>

        <div class="user-strip" aria-label="监控用户横向列表">
          <article
            v-for="(user, index) in users"
            :key="user.id"
            class="monitor-user-tile"
            :class="{
              selected: isUserSelected(user),
              expanded: expandedUserId === user.id,
              locked: isSelectionLocked(user),
              error: !!user.lastErrorType,
              paused: user.monitorStatus === 'PAUSED'
            }"
            :style="{
              '--tile-progress': `${collectProgress(user)}%`,
              '--tile-accent': userColor(user, index)
            }"
            role="button"
            tabindex="0"
            @click="toggleExpandedUser(user)"
            @keydown.enter.prevent="toggleExpandedUser(user)"
            @keydown.space.prevent="toggleExpandedUser(user)"
          >
            <div class="tile-main">
              <UserIdentity :user="user" />
              <div class="tile-quick-actions" @click.stop>
                <el-tooltip content="打开主页">
                  <a
                    class="profile-shortcut"
                    :href="userProfileUrl(user)"
                    target="_blank"
                    rel="noreferrer"
                    title="打开主页"
                    @click.stop
                    @pointerdown.stop
                  >
                    <el-icon><LinkIcon /></el-icon>
                  </a>
                </el-tooltip>
                <button
                  class="trend-select-button"
                  :class="{ active: isUserSelected(user) }"
                  type="button"
                  :disabled="isSelectionLocked(user)"
                  :title="isUserSelected(user) ? '移出趋势图' : isSelectionLocked(user) ? '最多选择 4 个趋势用户' : '加入趋势图'"
                  @click.stop="toggleTrendUser(user)"
                  @pointerdown.stop
                >
                  {{ isUserSelected(user) ? '已选' : isSelectionLocked(user) ? '上限' : '趋势' }}
                </button>
              </div>
            </div>

            <div class="tile-compact-line">
              <strong :class="deltaClass(user.deltaSincePrevious)">{{ formatDelta(user.deltaSincePrevious) }}</strong>
              <span class="state-pill state-text" :class="stateClass(user)">{{ stateText(user) }}</span>
            </div>

            <div class="tile-status-line">
              <span>{{ formatShortCount(user.currentFollowerCount) }}</span>
              <span>{{ nextCollectBrief(user) }}</span>
            </div>

            <div class="collect-rhythm">
              <div class="rhythm-bar"><span /></div>
            </div>
          </article>
        </div>

        <Transition name="detail-card">
          <article
            v-if="expandedUser"
            class="monitor-user-detail"
            :style="expandedUserStyle"
          >
            <div class="detail-hero">
              <UserIdentity :user="expandedUser" />
              <div class="detail-primary-metrics">
                <div>
                  <span>当前粉丝</span>
                  <strong>{{ formatShortCount(expandedUser.currentFollowerCount) }}</strong>
                </div>
                <div>
                  <span>最近变化</span>
                  <strong :class="deltaClass(expandedUser.deltaSincePrevious)">{{ formatDelta(expandedUser.deltaSincePrevious) }}</strong>
                </div>
              </div>
              <div class="detail-actions" @click.stop>
                <a
                  class="detail-profile-link"
                  :href="userProfileUrl(expandedUser)"
                  target="_blank"
                  rel="noreferrer"
                  title="打开 Bilibili 用户主页"
                  @click.stop
                  @pointerdown.stop
                >
                  <el-icon><LinkIcon /></el-icon>
                  打开主页
                </a>
                <UserActions :user="expandedUser" @refresh="refreshUser" @toggle="toggleUser" @remove="removeUser" />
              </div>
            </div>

            <div class="detail-grid">
              <div>
                <span>UID</span>
                <strong>{{ expandedUser.mid }}</strong>
              </div>
              <div>
                <span>监控状态</span>
                <strong>{{ stateText(expandedUser) }}</strong>
              </div>
              <div>
                <span>最后采集</span>
                <strong>{{ formatDateTime(expandedUser.lastSnapshotAt || expandedUser.lastSuccessAt) }}</strong>
              </div>
              <div>
                <span>下次采集</span>
                <strong>{{ expandedUser.monitorStatus === 'PAUSED' ? '已暂停' : formatDateTime(expandedUser.nextCollectAt) }}</strong>
              </div>
            </div>

            <div class="detail-footer" @click.stop>
              <IntervalEditor
                :user="expandedUser"
                :model-value="intervalDraft(expandedUser)"
                :saving="!!savingIntervals[expandedUser.id]"
                :dirty="isIntervalDirty(expandedUser)"
                @update:model-value="setIntervalDraft(expandedUser, $event)"
                @save="saveInterval(expandedUser)"
              />
              <div class="detail-rhythm">
                <span>距下次采集</span>
                <div class="collect-rhythm">
                  <div class="rhythm-bar"><span /></div>
                </div>
                <strong>{{ collectRhythmText(expandedUser) }}</strong>
              </div>
            </div>
          </article>
        </Transition>
      </section>

      <section class="trend-section">
        <div class="section-heading">
          <div>
            <h2>趋势图</h2>
            <p>{{ trendHelpText }}</p>
          </div>
          <div class="layout-pills">
            <Transition name="refresh-pill">
              <span
                v-if="trendRefreshIndicator !== 'idle'"
                class="refresh-pill"
                :class="trendRefreshIndicator"
              >
                {{ trendRefreshIndicatorText }}
              </span>
            </Transition>
            <span :class="{ active: selectedTrendUsers.length === 1 }">单图</span>
            <span :class="{ active: selectedTrendUsers.length === 2 }">双列</span>
            <span :class="{ active: selectedTrendUsers.length === 3 }">主次</span>
            <span :class="{ active: selectedTrendUsers.length === 4 }">2x2</span>
          </div>
        </div>

        <el-empty
          v-if="selectedTrendUsers.length === 0"
          class="trend-empty"
          description="请选择上方用户查看趋势"
        />

        <TransitionGroup
          v-else
          tag="div"
          class="trend-board"
          :class="`chart-count-${selectedTrendUsers.length}`"
          name="trend-card"
        >
          <article
            v-for="(user, index) in selectedTrendUsers"
            :key="user.id"
            class="trend-card"
            :data-user-id="user.id"
            :class="{
              featured: selectedTrendUsers.length === 1 || (selectedTrendUsers.length === 3 && index === 0),
              dragging: draggedTrendUserId === user.id,
              'drop-target': dragOverTrendUserId === user.id && draggedTrendUserId !== user.id
            }"
            @pointerdown="startPointerTrendDrag(user, $event)"
          >
            <div class="panel-title">
              <div class="panel-heading-copy">
                <div class="title-row">
                  <strong>{{ user.nickname }}</strong>
                  <span v-if="selectedTrendUsers.length > 1" class="drag-affordance" title="拖动交换图表顺序">
                    <el-icon><Rank /></el-icon>
                    拖动
                  </span>
                </div>
                <span>UID {{ user.mid }} · {{ points(user).length }} 个点</span>
              </div>
              <div class="panel-metric">
                <strong>{{ formatShortCount(user.currentFollowerCount) }}</strong>
                <span :class="deltaClass(user.deltaSincePrevious)">{{ formatDelta(user.deltaSincePrevious) }}</span>
              </div>
            </div>
            <TrendChart
              :labels="chartLabels(user)"
              :timestamps="chartTimestamps(user)"
              :values="chartValues(user)"
              :height="chartHeight(index)"
              :theme="theme"
              :accent-color="userColor(user, index)"
            />
          </article>
        </TransitionGroup>
      </section>
    </template>

    <el-alert
      v-if="apiError"
      class="error-alert"
      type="error"
      :title="apiError"
      show-icon
      :closable="true"
      @close="apiError = ''"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Check, Delete, Link as LinkIcon, Moon, Plus, Rank, Refresh, Sunny } from '@element-plus/icons-vue'
import { ElButton, ElInputNumber, ElMessage, ElMessageBox, ElSwitch, ElTooltip } from 'element-plus'
import TrendChart from '@/components/charts/TrendChart.vue'
import BilibiliAuthPanel from './components/BilibiliAuthPanel.vue'
import {
  addBilibiliMonitorUser,
  deleteBilibiliMonitorUser,
  fetchBilibiliMonitorUsers,
  fetchBilibiliTrends,
  refreshBilibiliMonitorUser,
  updateBilibiliMonitorSettings,
  updateBilibiliMonitorStatus,
  type BilibiliFollowerPoint,
  type BilibiliMonitorUser
} from '@/api/bilibili'

type MonitorTheme = 'light' | 'dark'

const MIN_INTERVAL_SECONDS = 1
const MAX_INTERVAL_SECONDS = 2_592_000
const SHORT_INTERVAL_WARNING_SECONDS = 60
const MAX_SELECTED_TRENDS = 4
const AUTO_COLLECT_TICK_MS = 1000
const AUTO_COLLECT_DUE_GRACE_MS = 250
const MAX_AUTO_COLLECT_PER_TICK = 1
const TREND_REFRESH_MIN_VISIBLE_MS = 700
const TREND_REFRESH_DONE_VISIBLE_MS = 900
const THEME_STORAGE_KEY = 'bilibili-monitor-theme'
const USER_COLORS = ['#2454a6', '#a970ff', '#ec5fa8', '#0ea5a3', '#2f80ed', '#16a34a', '#f97316', '#64748b']

const loading = ref(false)
const adding = ref(false)
const apiError = ref('')
const users = ref<BilibiliMonitorUser[]>([])
const selectedUserIds = ref<number[]>([])
const expandedUserId = ref<number>()
const trendPointsByUserId = ref<Record<number, BilibiliFollowerPoint[]>>({})
const trendRefreshing = ref(false)
const trendRefreshIndicator = ref<'idle' | 'refreshing' | 'done' | 'error'>('idle')
const autoCollectingUserIds = ref<number[]>([])
const intervalDrafts = reactive<Record<number, number>>({})
const dirtyIntervalUserIds = ref<number[]>([])
const savingIntervals = reactive<Record<number, boolean>>({})
const theme = ref<MonitorTheme>('light')
const now = ref(Date.now())
const draggedTrendUserId = ref<number>()
const dragOverTrendUserId = ref<number>()
const pointerDragSourceId = ref<number>()
const pointerDragStart = ref({ x: 0, y: 0 })
const pointerDragMoved = ref(false)
let trendRefreshTimer: number | undefined
let trendRefreshIndicatorTimer: number | undefined
let autoCollectTimer: number | undefined
let autoCollectTickRunning = false
let usersLoadRequest: Promise<void> | undefined
let pendingTrendRefresh = false
let trendRefreshKey = ''
let trendRefreshIndicatorStartedAt = 0
const lastAutoCollectStartedAt = new Map<number, number>()

const addForm = reactive({
  mid: '',
  intervalSeconds: 3600
})

const isDarkTheme = computed({
  get: () => theme.value === 'dark',
  set: (value: boolean) => {
    theme.value = value ? 'dark' : 'light'
  }
})
const themeClass = computed(() => (theme.value === 'dark' ? 'is-dark' : 'is-light'))
const activeCount = computed(() => users.value.filter((user) => user.monitorStatus === 'ACTIVE').length)
const errorCount = computed(() => users.value.filter((user) => user.lastErrorType).length)
const totalFollowers = computed(() =>
  users.value.reduce((sum, user) => sum + (user.currentFollowerCount ?? 0), 0)
)
const totalDelta = computed(() =>
  users.value.reduce((sum, user) => sum + (user.deltaSincePrevious ?? 0), 0)
)
const selectedTrendUsers = computed(() =>
  selectedUserIds.value
    .map((id) => users.value.find((user) => user.id === id))
    .filter((user): user is BilibiliMonitorUser => Boolean(user))
)
const expandedUser = computed(() => users.value.find((user) => user.id === expandedUserId.value))
const expandedUserStyle = computed(() => {
  const user = expandedUser.value
  if (!user) return {}
  return {
    '--tile-progress': `${collectProgress(user)}%`,
    '--tile-accent': userColor(user, users.value.findIndex((item) => item.id === user.id))
  }
})
const nextCollectText = computed(() => {
  const next = users.value
    .map((user) => user.nextCollectAt)
    .filter(Boolean)
    .sort()[0]
  return next ? formatDateTime(next) : '未排程'
})
const intervalText = computed(() => {
  const intervals = [...new Set(users.value.map((user) => user.intervalSeconds).filter(Boolean))]
  if (!intervals.length) return '默认'
  if (intervals.length === 1) return formatInterval(intervals[0])
  return `${formatInterval(Math.min(...intervals))} - ${formatInterval(Math.max(...intervals))}`
})
const shortIntervalNotice = computed(() => {
  return (
    addForm.intervalSeconds < SHORT_INTERVAL_WARNING_SECONDS ||
    users.value.some((user) => intervalDraft(user) < SHORT_INTERVAL_WARNING_SECONDS)
  )
})
const trendHelpText = computed(() => {
  if (!selectedTrendUsers.value.length) return '点击上方用户卡片中的趋势按钮开始对比，最多同时查看 4 个用户。'
  if (selectedTrendUsers.value.length === 1) return '单个用户使用大面积图表，适合观察连续变化。'
  if (selectedTrendUsers.value.length === 2) return '两个用户左右并排，适合横向对比节奏。'
  if (selectedTrendUsers.value.length === 3) return '三个用户采用主次布局，第一个选择会获得更大的图表区域。'
  return '四个用户使用 2x2 网格，适合外接屏或宽屏监控。'
})
const trendRefreshIndicatorText = computed(() => {
  if (trendRefreshIndicator.value === 'done') return '已更新'
  if (trendRefreshIndicator.value === 'error') return '刷新失败'
  return '刷新中'
})

watch(theme, (value) => {
  localStorage.setItem(THEME_STORAGE_KEY, value)
  applyDocumentTheme(value)
})

async function loadUsers(options: { silent?: boolean } = {}) {
  if (usersLoadRequest) return usersLoadRequest

  const showLoading = !options.silent
  if (showLoading) {
    loading.value = true
  }
  apiError.value = ''

  usersLoadRequest = (async () => {
    try {
      users.value = await fetchBilibiliMonitorUsers()
      syncIntervalDrafts()
      selectedUserIds.value = selectedUserIds.value.filter((id) => users.value.some((user) => user.id === id))
      if (expandedUserId.value && !users.value.some((user) => user.id === expandedUserId.value)) {
        expandedUserId.value = undefined
      }
      pruneTrendCache()
      if (selectedUserIds.value.length) {
        queueTrendRefresh(0)
      }
    } catch (error) {
      apiError.value = errorMessage(error)
    }
  })()

  try {
    await usersLoadRequest
  } finally {
    usersLoadRequest = undefined
    if (showLoading) {
      loading.value = false
    }
  }
}

async function submitAdd() {
  const mid = Number(addForm.mid)
  if (!Number.isInteger(mid) || mid <= 0) {
    ElMessage.warning('请输入有效 UID')
    return
  }
  adding.value = true
  apiError.value = ''
  try {
    const user = await addBilibiliMonitorUser({ mid, intervalSeconds: addForm.intervalSeconds })
    addForm.mid = ''
    await loadUsers()
    if (!selectedUserIds.value.includes(user.id) && selectedUserIds.value.length < MAX_SELECTED_TRENDS) {
      selectedUserIds.value = [...selectedUserIds.value, user.id]
      queueTrendRefresh()
    }
    ElMessage.success('已添加监控用户')
  } catch (error) {
    apiError.value = errorMessage(error)
  } finally {
    adding.value = false
  }
}

async function saveInterval(user: BilibiliMonitorUser) {
  const intervalSeconds = clampInterval(intervalDraft(user))
  if (intervalSeconds === user.intervalSeconds) return
  savingIntervals[user.id] = true
  apiError.value = ''
  try {
    const updatedUser = await updateBilibiliMonitorSettings(user.id, { intervalSeconds })
    intervalDrafts[user.id] = updatedUser.intervalSeconds ?? intervalSeconds
    setIntervalDirty(user.id, false)
    await loadUsers()
    ElMessage.success(`已更新采集间隔为 ${formatInterval(intervalSeconds)}`)
  } catch (error) {
    apiError.value = errorMessage(error)
  } finally {
    savingIntervals[user.id] = false
  }
}

async function refreshUser(user: BilibiliMonitorUser) {
  apiError.value = ''
  try {
    await refreshBilibiliMonitorUser(user.id)
    await loadUsers()
    ElMessage.success('已刷新')
  } catch (error) {
    apiError.value = errorMessage(error)
  }
}

async function runAutoCollectTick() {
  if (autoCollectTickRunning || !users.value.length) return

  const dueUsers = users.value
    .filter((user) => shouldAutoCollect(user))
    .sort((a, b) => nextCollectTime(a) - nextCollectTime(b))
    .slice(0, MAX_AUTO_COLLECT_PER_TICK)

  if (!dueUsers.length) return

  autoCollectTickRunning = true
  let shouldReload = false
  try {
    for (const user of dueUsers) {
      shouldReload = (await autoCollectUser(user)) || shouldReload
    }
    if (shouldReload) {
      await loadUsers({ silent: true })
    }
  } finally {
    autoCollectTickRunning = false
  }
}

async function autoCollectUser(user: BilibiliMonitorUser) {
  setAutoCollecting(user.id, true)
  lastAutoCollectStartedAt.set(user.id, Date.now())
  try {
    await refreshBilibiliMonitorUser(user.id)
    return true
  } catch (error) {
    apiError.value = errorMessage(error)
    return true
  } finally {
    setAutoCollecting(user.id, false)
  }
}

function shouldAutoCollect(user: BilibiliMonitorUser) {
  if (user.monitorStatus !== 'ACTIVE') return false
  if (isAutoCollecting(user.id) || savingIntervals[user.id]) return false

  const dueAt = nextCollectTime(user)
  if (!Number.isFinite(dueAt)) return false
  if (dueAt > Date.now() + AUTO_COLLECT_DUE_GRACE_MS) return false

  const lastStartedAt = lastAutoCollectStartedAt.get(user.id) ?? 0
  const guardMs = Math.max(1000, Math.min(user.intervalSeconds * 1000, 5000))
  return Date.now() - lastStartedAt >= guardMs
}

function nextCollectTime(user: BilibiliMonitorUser) {
  const fromServer = user.nextCollectAt ? Date.parse(user.nextCollectAt) : Number.NaN
  if (Number.isFinite(fromServer)) return fromServer

  const lastSnapshotAt = user.lastSnapshotAt || user.lastSuccessAt
  const fallbackStart = lastSnapshotAt ? Date.parse(lastSnapshotAt) : Number.NaN
  if (Number.isFinite(fallbackStart)) {
    return fallbackStart + user.intervalSeconds * 1000
  }

  return Date.now()
}

function setAutoCollecting(userId: number, collecting: boolean) {
  const nextIds = new Set(autoCollectingUserIds.value)
  if (collecting) {
    nextIds.add(userId)
  } else {
    nextIds.delete(userId)
  }
  autoCollectingUserIds.value = [...nextIds]
}

function isAutoCollecting(userId: number) {
  return autoCollectingUserIds.value.includes(userId)
}

async function toggleUser(user: BilibiliMonitorUser, enabled: boolean) {
  apiError.value = ''
  try {
    await updateBilibiliMonitorStatus(user.id, enabled)
    await loadUsers()
  } catch (error) {
    apiError.value = errorMessage(error)
  }
}

async function removeUser(user: BilibiliMonitorUser) {
  await ElMessageBox.confirm(`删除 ${user.nickname} 的监控记录？`, '删除监控用户', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  apiError.value = ''
  try {
    await deleteBilibiliMonitorUser(user.id)
    selectedUserIds.value = selectedUserIds.value.filter((id) => id !== user.id)
    if (expandedUserId.value === user.id) {
      expandedUserId.value = undefined
    }
    const nextTrendPoints = { ...trendPointsByUserId.value }
    delete nextTrendPoints[user.id]
    trendPointsByUserId.value = nextTrendPoints
    await loadUsers()
    ElMessage.success('已删除')
  } catch (error) {
    apiError.value = errorMessage(error)
  }
}

function toggleExpandedUser(user: BilibiliMonitorUser) {
  expandedUserId.value = expandedUserId.value === user.id ? undefined : user.id
}

function toggleTrendUser(user: BilibiliMonitorUser) {
  if (isUserSelected(user)) {
    selectedUserIds.value = selectedUserIds.value.filter((id) => id !== user.id)
    queueTrendRefresh()
    return
  }
  if (selectedUserIds.value.length >= MAX_SELECTED_TRENDS) {
    ElMessage.warning('最多选择 4 个用户查看趋势')
    return
  }
  selectedUserIds.value = [...selectedUserIds.value, user.id]
  queueTrendRefresh()
}

function queueTrendRefresh(delay = 180) {
  if (trendRefreshTimer) {
    window.clearTimeout(trendRefreshTimer)
    trendRefreshTimer = undefined
  }
  if (!selectedUserIds.value.length) {
    pendingTrendRefresh = false
    return
  }
  trendRefreshTimer = window.setTimeout(() => {
    trendRefreshTimer = undefined
    refreshSelectedTrendData()
  }, delay)
}

async function refreshSelectedTrendData() {
  const userIds = [...selectedUserIds.value]
  if (!userIds.length) return

  const nextKey = userIds.join(',')
  if (trendRefreshing.value) {
    pendingTrendRefresh = trendRefreshKey !== nextKey || pendingTrendRefresh
    return
  }

  trendRefreshing.value = true
  trendRefreshKey = nextKey
  showTrendRefreshIndicator()
  let refreshFailed = false
  try {
    const trends = await fetchBilibiliTrends(userIds, 500)
    const nextTrendPoints = { ...trendPointsByUserId.value }
    trends.forEach((trend) => {
      nextTrendPoints[trend.user.id] = trend.points
    })
    trendPointsByUserId.value = nextTrendPoints
  } catch (error) {
    refreshFailed = true
    apiError.value = errorMessage(error)
  } finally {
    trendRefreshing.value = false
    trendRefreshKey = ''
    if (pendingTrendRefresh) {
      pendingTrendRefresh = false
      queueTrendRefresh(120)
    } else {
      finishTrendRefreshIndicator(refreshFailed ? 'error' : 'done')
    }
  }
}

function showTrendRefreshIndicator() {
  if (trendRefreshIndicatorTimer) {
    window.clearTimeout(trendRefreshIndicatorTimer)
    trendRefreshIndicatorTimer = undefined
  }
  trendRefreshIndicatorStartedAt = Date.now()
  trendRefreshIndicator.value = 'refreshing'
}

function finishTrendRefreshIndicator(result: 'done' | 'error') {
  const elapsed = Date.now() - trendRefreshIndicatorStartedAt
  const remaining = Math.max(0, TREND_REFRESH_MIN_VISIBLE_MS - elapsed)
  if (trendRefreshIndicatorTimer) {
    window.clearTimeout(trendRefreshIndicatorTimer)
  }
  trendRefreshIndicatorTimer = window.setTimeout(() => {
    trendRefreshIndicator.value = result
    trendRefreshIndicatorTimer = window.setTimeout(() => {
      trendRefreshIndicator.value = 'idle'
      trendRefreshIndicatorTimer = undefined
    }, TREND_REFRESH_DONE_VISIBLE_MS)
  }, remaining)
}

function pruneTrendCache() {
  const liveIds = new Set(users.value.map((user) => user.id))
  const selectedIds = new Set(selectedUserIds.value)
  const nextTrendPoints: Record<number, BilibiliFollowerPoint[]> = {}
  Object.entries(trendPointsByUserId.value).forEach(([id, points]) => {
    const userId = Number(id)
    if (liveIds.has(userId) && selectedIds.has(userId)) {
      nextTrendPoints[userId] = points
    }
  })
  trendPointsByUserId.value = nextTrendPoints
}

function swapTrendCards(sourceId: number, targetId: number) {
  if (sourceId === targetId) return false
  const sourceIndex = selectedUserIds.value.indexOf(sourceId)
  const targetIndex = selectedUserIds.value.indexOf(targetId)
  if (sourceIndex < 0 || targetIndex < 0) {
    return false
  }
  const next = [...selectedUserIds.value]
  ;[next[sourceIndex], next[targetIndex]] = [next[targetIndex], next[sourceIndex]]
  selectedUserIds.value = next
  return true
}

function endTrendDrag() {
  draggedTrendUserId.value = undefined
  dragOverTrendUserId.value = undefined
}

function startPointerTrendDrag(user: BilibiliMonitorUser, event: PointerEvent) {
  if (selectedTrendUsers.value.length < 2 || event.button !== 0 || isDragIgnoredTarget(event.target)) return
  const currentTarget = event.currentTarget as HTMLElement | null
  pointerDragSourceId.value = user.id
  pointerDragStart.value = { x: event.clientX, y: event.clientY }
  pointerDragMoved.value = false
  currentTarget?.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', movePointerTrendDrag, { passive: false })
  window.addEventListener('pointerup', finishPointerTrendDrag, { once: true })
  window.addEventListener('pointercancel', cancelPointerTrendDrag, { once: true })
}

function movePointerTrendDrag(event: PointerEvent) {
  const sourceId = pointerDragSourceId.value
  if (!sourceId) return

  const dx = event.clientX - pointerDragStart.value.x
  const dy = event.clientY - pointerDragStart.value.y
  if (!pointerDragMoved.value && Math.hypot(dx, dy) < 8) return

  pointerDragMoved.value = true
  draggedTrendUserId.value = sourceId
  event.preventDefault()

  const targetId = trendCardIdFromPoint(event.clientX, event.clientY, sourceId)
  dragOverTrendUserId.value = targetId && targetId !== sourceId ? targetId : undefined
}

function finishPointerTrendDrag(event: PointerEvent) {
  const sourceId = pointerDragSourceId.value
  const targetId = sourceId ? trendCardIdFromPoint(event.clientX, event.clientY, sourceId) ?? dragOverTrendUserId.value : undefined
  if (sourceId && targetId && pointerDragMoved.value) {
    if (swapTrendCards(sourceId, targetId)) {
      queueTrendRefresh()
    }
  }
  cleanupPointerTrendDrag()
  endTrendDrag()
}

function cancelPointerTrendDrag() {
  cleanupPointerTrendDrag()
  endTrendDrag()
}

function cleanupPointerTrendDrag() {
  pointerDragSourceId.value = undefined
  pointerDragMoved.value = false
  window.getSelection()?.removeAllRanges()
  window.removeEventListener('pointermove', movePointerTrendDrag)
  window.removeEventListener('pointerup', finishPointerTrendDrag)
  window.removeEventListener('pointercancel', cancelPointerTrendDrag)
}

function trendCardIdFromPoint(x: number, y: number, sourceId?: number) {
  const board = document.querySelector<HTMLElement>('.trend-board')
  const boardRect = board?.getBoundingClientRect()
  if (!boardRect || x < boardRect.left - 24 || x > boardRect.right + 24 || y < boardRect.top - 24 || y > boardRect.bottom + 24) {
    return undefined
  }

  const cards = selectedUserIds.value
    .map((id) => {
      const element = document.querySelector<HTMLElement>(`.trend-card[data-user-id="${id}"]`)
      return element ? { id, rect: element.getBoundingClientRect() } : undefined
    })
    .filter((item): item is { id: number; rect: DOMRect } => item != null && item.id !== sourceId)

  const directHit = cards.find(({ rect }) => x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom)
  if (directHit) return directHit.id

  const nearest = cards
    .map(({ id, rect }) => {
      const centerX = rect.left + rect.width / 2
      const centerY = rect.top + rect.height / 2
      return { id, distance: Math.hypot(x - centerX, y - centerY) }
    })
    .sort((a, b) => a.distance - b.distance)[0]

  return nearest?.id
}

function isDragIgnoredTarget(target: EventTarget | null) {
  const element = target instanceof Element ? target : null
  return Boolean(element?.closest('button, a, input, textarea, select, .el-switch, .el-input-number'))
}

function isUserSelected(user: BilibiliMonitorUser) {
  return selectedUserIds.value.includes(user.id)
}

function isSelectionLocked(user: BilibiliMonitorUser) {
  return !isUserSelected(user) && selectedUserIds.value.length >= MAX_SELECTED_TRENDS
}

function focusMidInput() {
  const input = document.querySelector<HTMLInputElement>('.mid-input input')
  input?.focus()
}

function points(user: BilibiliMonitorUser) {
  const refreshedPoints = trendPointsByUserId.value[user.id]
  if (refreshedPoints?.length) return refreshedPoints
  if (user.recentTrend?.length) return user.recentTrend
  if (user.currentFollowerCount == null) return []
  return [
    {
      capturedAt: user.lastSnapshotAt || new Date().toISOString(),
      followerCount: user.currentFollowerCount,
      followingCount: user.followingCount,
      sourceEndpoint: user.sourceEndpoint || 'local'
    }
  ]
}

function chartLabels(user: BilibiliMonitorUser) {
  return points(user).map((point) => formatChartTime(point.capturedAt))
}

function chartTimestamps(user: BilibiliMonitorUser) {
  return points(user).map((point) => point.capturedAt)
}

function chartValues(user: BilibiliMonitorUser) {
  return points(user).map((point) => point.followerCount)
}

function chartHeight(index: number) {
  const count = selectedTrendUsers.value.length
  if (count === 1) return 440
  if (count === 2) return 360
  if (count === 3) return index === 0 ? 440 : 204
  return 248
}

function syncIntervalDrafts() {
  const liveUserIds = new Set(users.value.map((user) => user.id))
  Object.keys(intervalDrafts).forEach((id) => {
    if (!liveUserIds.has(Number(id))) {
      delete intervalDrafts[Number(id)]
    }
  })
  dirtyIntervalUserIds.value = dirtyIntervalUserIds.value.filter((userId) => liveUserIds.has(userId))

  users.value.forEach((user) => {
    if (!isIntervalDirty(user)) {
      intervalDrafts[user.id] = user.intervalSeconds
    }
  })
}

function intervalDraft(user: BilibiliMonitorUser) {
  return intervalDrafts[user.id] ?? user.intervalSeconds
}

function setIntervalDraft(user: BilibiliMonitorUser, value: number | undefined) {
  const nextValue = clampInterval(value ?? MIN_INTERVAL_SECONDS)
  intervalDrafts[user.id] = nextValue
  setIntervalDirty(user.id, nextValue !== user.intervalSeconds)
}

function setIntervalDirty(userId: number, dirty: boolean) {
  const nextIds = new Set(dirtyIntervalUserIds.value)
  if (dirty) {
    nextIds.add(userId)
  } else {
    nextIds.delete(userId)
  }
  dirtyIntervalUserIds.value = [...nextIds]
}

function isIntervalDirty(user: BilibiliMonitorUser) {
  return dirtyIntervalUserIds.value.includes(user.id) && intervalDraft(user) !== user.intervalSeconds
}

function clampInterval(value: number) {
  if (!Number.isFinite(value)) return MIN_INTERVAL_SECONDS
  return Math.min(MAX_INTERVAL_SECONDS, Math.max(MIN_INTERVAL_SECONDS, Math.round(value)))
}

function intervalStep(value: number) {
  if (value < 60) return 1
  if (value < 3600) return 60
  if (value < 86400) return 300
  return 3600
}

function formatInterval(value?: number) {
  if (!value) return '-'
  if (value < 60) return `${value} 秒`
  if (value < 3600) {
    const minutes = Math.floor(value / 60)
    const seconds = value % 60
    return seconds ? `${minutes} 分 ${seconds} 秒` : `${minutes} 分`
  }
  if (value < 86400) {
    const hours = Math.floor(value / 3600)
    const minutes = Math.floor((value % 3600) / 60)
    return minutes ? `${hours} 小时 ${minutes} 分` : `${hours} 小时`
  }
  const days = Math.floor(value / 86400)
  const hours = Math.floor((value % 86400) / 3600)
  return hours ? `${days} 天 ${hours} 小时` : `${days} 天`
}

function formatCount(value?: number) {
  if (value == null) return '-'
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatShortCount(value?: number) {
  if (value == null) return '-'
  if (Math.abs(value) >= 100000000) return `${(value / 100000000).toFixed(1)}亿`
  if (Math.abs(value) >= 10000) return `${(value / 10000).toFixed(1)}万`
  return formatCount(value)
}

function formatDelta(value?: number) {
  if (value == null) return '-'
  if (value === 0) return '0'
  const formatted = Math.abs(value) >= 10000 ? formatShortCount(value) : formatCount(value)
  return `${value > 0 ? '+' : ''}${formatted}`
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date(value))
}

function formatChartTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date(value))
}

function nextCollectBrief(user: BilibiliMonitorUser) {
  if (user.monitorStatus === 'PAUSED') return '已暂停'
  if (user.lastErrorType) return user.lastErrorType
  if (isAutoCollecting(user.id)) return '采集中'
  const nextTime = nextCollectTime(user)
  if (!Number.isFinite(nextTime)) return '未排程'
  const diff = nextTime - now.value
  if (diff <= 0) return '待采集'
  if (diff < 60_000) return `${Math.ceil(diff / 1000)} 秒后`
  if (diff < 3_600_000) return `${Math.ceil(diff / 60_000)} 分后`
  if (diff < 86_400_000) return `${Math.ceil(diff / 3_600_000)} 小时后`
  return `${Math.ceil(diff / 86_400_000)} 天后`
}

function collectRhythmText(user: BilibiliMonitorUser) {
  const brief = nextCollectBrief(user)
  if (['已暂停', '采集中', '待采集', '未排程'].includes(brief) || user.lastErrorType) {
    return brief
  }
  return `${brief} · ${Math.round(collectProgress(user))}%`
}

function collectProgress(user: BilibiliMonitorUser) {
  if (user.monitorStatus === 'PAUSED') return 0
  if (isAutoCollecting(user.id)) return 100
  if (user.lastErrorType) return 100
  const end = nextCollectTime(user)
  if (!Number.isFinite(end)) return 0

  const lastSnapshotAt = user.lastSnapshotAt || user.lastSuccessAt
  let start = lastSnapshotAt ? Date.parse(lastSnapshotAt) : Number.NaN
  if (!Number.isFinite(start)) {
    start = end - user.intervalSeconds * 1000
  }
  if (!Number.isFinite(start)) return 0
  if (end <= start) return now.value >= end ? 100 : 0
  return Math.min(100, Math.max(4, ((now.value - start) / (end - start)) * 100))
}

function deltaClass(value?: number) {
  return {
    up: (value ?? 0) > 0,
    down: (value ?? 0) < 0,
    flat: !value
  }
}

function stateText(user: BilibiliMonitorUser) {
  if (user.lastErrorType) return '异常'
  return user.monitorStatus === 'ACTIVE' ? '启用' : '停用'
}

function stateClass(user: BilibiliMonitorUser) {
  return {
    active: user.monitorStatus === 'ACTIVE' && !user.lastErrorType,
    paused: user.monitorStatus === 'PAUSED',
    error: !!user.lastErrorType
  }
}

function userColor(user: BilibiliMonitorUser, index: number) {
  const selectedIndex = selectedUserIds.value.indexOf(user.id)
  return USER_COLORS[(selectedIndex >= 0 ? selectedIndex : index) % USER_COLORS.length]
}

function errorMessage(error: unknown) {
  const maybe = error as { response?: { data?: { message?: string } }; message?: string }
  return maybe.response?.data?.message || maybe.message || '请求失败'
}

function avatarFallbackText(user: BilibiliMonitorUser) {
  const text = user.nickname?.trim()
  return text ? text.slice(0, 1).toUpperCase() : String(user.mid).slice(-2)
}

function userProfileUrl(user: BilibiliMonitorUser) {
  return user.profileUrl || `https://space.bilibili.com/${user.mid}`
}

function applyDocumentTheme(value: MonitorTheme) {
  document.documentElement.dataset.bilibiliTheme = value
}

function loadStoredTheme() {
  const stored = localStorage.getItem(THEME_STORAGE_KEY)
  theme.value = stored === 'dark' ? 'dark' : 'light'
  applyDocumentTheme(theme.value)
}

function startAutoCollectScheduler() {
  if (autoCollectTimer) return
  autoCollectTimer = window.setInterval(() => {
    now.value = Date.now()
    void runAutoCollectTick()
  }, AUTO_COLLECT_TICK_MS)
}

const IntervalEditor = defineComponent({
  props: {
    user: { type: Object as () => BilibiliMonitorUser, required: true },
    modelValue: { type: Number, required: true },
    saving: { type: Boolean, default: false },
    dirty: { type: Boolean, default: false },
    compact: { type: Boolean, default: false }
  },
  emits: ['update:modelValue', 'save'],
  setup(props, { emit }) {
    return () =>
      h('div', {
        class: ['interval-editor', { compact: props.compact, short: props.modelValue < SHORT_INTERVAL_WARNING_SECONDS }]
      }, [
        h('span', { class: 'interval-label' }, props.compact ? '间隔' : props.dirty ? '采集间隔（未保存）' : '采集间隔'),
        h(ElInputNumber, {
          modelValue: props.modelValue,
          min: MIN_INTERVAL_SECONDS,
          max: MAX_INTERVAL_SECONDS,
          step: intervalStep(props.modelValue),
          controls: false,
          size: props.compact ? 'small' : 'default',
          class: 'inline-interval-input',
          'onUpdate:modelValue': (value: number | undefined) => emit('update:modelValue', value)
        }),
        h('span', { class: 'unit' }, '秒'),
        h(ElButton, {
          icon: Check,
          type: 'primary',
          plain: true,
          size: props.compact ? 'small' : 'default',
          loading: props.saving,
          disabled: !props.dirty || props.saving,
          onClick: () => emit('save')
        }, () => (props.compact ? '' : props.dirty ? '保存修改' : '已保存'))
      ])
  }
})

const UserIdentity = defineComponent({
  props: {
    user: { type: Object as () => BilibiliMonitorUser, required: true }
  },
  setup(props) {
    const imageFailed = ref(false)
    watch(() => props.user.avatarUrl, () => {
      imageFailed.value = false
    })
    return () =>
      h('div', { class: 'user-identity' }, [
        h('div', { class: 'avatar-shell' }, props.user.avatarUrl && !imageFailed.value
          ? [
              h('img', {
                src: props.user.avatarUrl,
                alt: props.user.nickname,
                referrerpolicy: 'no-referrer',
                onError: () => {
                  imageFailed.value = true
                }
              })
            ]
          : [h('span', avatarFallbackText(props.user))]),
        h('div', { class: 'identity-copy' }, [
          h('strong', { title: props.user.nickname }, props.user.nickname),
          h('span', `UID ${props.user.mid}`)
        ])
      ])
  }
})

const UserActions = defineComponent({
  props: {
    user: { type: Object as () => BilibiliMonitorUser, required: true },
    compact: { type: Boolean, default: false }
  },
  emits: ['refresh', 'toggle', 'remove'],
    setup(props, { emit }) {
    return () =>
      h('div', { class: ['user-actions', { compact: props.compact }] }, [
        h(
          ElTooltip,
          { content: props.user.monitorStatus === 'ACTIVE' ? '停用监控' : '启用监控' },
          {
            default: () =>
              h(ElSwitch, {
                modelValue: props.user.monitorStatus === 'ACTIVE',
                size: props.compact ? 'small' : 'default',
                onChange: (value: string | number | boolean) => emit('toggle', props.user, value === true)
              })
          }
        ),
        h(
          ElTooltip,
          { content: '立即采集' },
          {
            default: () =>
              h(
                'button',
                { class: 'icon-button', type: 'button', onClick: () => emit('refresh', props.user) },
                [h(Refresh)]
              )
          }
        ),
        h(
          ElTooltip,
          { content: '删除监控' },
          {
            default: () =>
              h(
                'button',
                { class: 'icon-button danger', type: 'button', onClick: () => emit('remove', props.user) },
                [h(Delete)]
              )
          }
        )
      ])
  }
})

onMounted(() => {
  loadStoredTheme()
  now.value = Date.now()
  startAutoCollectScheduler()
  loadUsers().then(() => {
    void runAutoCollectTick()
  })
})

onBeforeUnmount(() => {
  if (trendRefreshTimer) window.clearTimeout(trendRefreshTimer)
  if (trendRefreshIndicatorTimer) window.clearTimeout(trendRefreshIndicatorTimer)
  if (autoCollectTimer) window.clearInterval(autoCollectTimer)
  cleanupPointerTrendDrag()
  delete document.documentElement.dataset.bilibiliTheme
})
</script>

<style>
html[data-bilibili-theme='dark'] .shell {
  background: #172033;
}

html[data-bilibili-theme='dark'] .sidebar,
html[data-bilibili-theme='dark'] .header {
  border-color: #31405c;
  background: #1b263b;
  color: #f4f7fb;
}

html[data-bilibili-theme='dark'] .brand,
html[data-bilibili-theme='dark'] .header {
  border-color: #31405c;
}

html[data-bilibili-theme='dark'] .brand span,
html[data-bilibili-theme='dark'] .header-subtitle {
  color: #b5c1d6;
}

html[data-bilibili-theme='dark'] .menu {
  --el-menu-bg-color: #1b263b;
  --el-menu-text-color: #cbd6e8;
  --el-menu-hover-bg-color: #26344e;
  --el-menu-active-color: #9fc9ff;
}

html[data-bilibili-theme='dark'] .main {
  background: #172033;
}

.bilibili-page {
  --page-bg: #f7f4f0;
  --surface: #fffefa;
  --surface-strong: #ffffff;
  --border: #e5d7c8;
  --border-soft: #f0e5d9;
  --text: #081329;
  --muted: #5b667a;
  --muted-strong: #344054;
  --accent: #2f6df6;
  --accent-soft: #eaf1ff;
  --positive: #00845f;
  --negative: #c23a2b;
  --warning: #b76e00;
  --shadow: 0 14px 36px rgba(29, 41, 57, 0.08);
  gap: 14px;
  min-height: calc(100vh - 104px);
  color: var(--text);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.7), rgba(255, 255, 255, 0)),
    var(--page-bg);
}

.bilibili-page.is-dark {
  --page-bg: #172033;
  --surface: rgba(31, 43, 67, 0.9);
  --surface-strong: rgba(38, 52, 80, 0.92);
  --border: rgba(86, 105, 140, 0.62);
  --border-soft: rgba(64, 81, 113, 0.72);
  --text: #f4f7fb;
  --muted: #aebbd0;
  --muted-strong: #d2dbea;
  --accent: #83b8ff;
  --accent-soft: rgba(131, 184, 255, 0.15);
  --positive: #57d4a7;
  --negative: #ff8e9b;
  --warning: #f0c77a;
  --shadow: 0 18px 42px rgba(7, 12, 24, 0.28);
  background:
    radial-gradient(circle at 18% 0%, rgba(131, 184, 255, 0.16), transparent 36%),
    radial-gradient(circle at 84% 12%, rgba(220, 156, 255, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.026), transparent 46%),
    var(--page-bg);
}

.monitor-header {
  align-items: flex-start;
  padding: 10px 16px 8px;
  border-radius: 8px;
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--surface) 54%, transparent), transparent 72%);
}

.monitor-header .page-title {
  margin-bottom: 8px;
  color: var(--text);
  letter-spacing: 0;
  line-height: 1.25;
}

.monitor-header .page-subtitle {
  margin-top: 0;
  color: var(--muted);
}

.control-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px 12px;
  min-width: 0;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 7px 11px;
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--muted-strong);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface-strong) 94%, transparent), var(--surface));
  font-size: 12px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.05);
}

.bilibili-page.is-dark .theme-toggle {
  border-color: rgba(102, 124, 162, 0.5);
  background:
    linear-gradient(180deg, rgba(48, 64, 96, 0.92), rgba(35, 49, 77, 0.92));
  box-shadow: 0 12px 28px rgba(7, 12, 24, 0.18);
}

.theme-toggle.dark {
  color: var(--muted-strong);
}

.theme-label {
  padding-right: 2px;
  color: var(--text);
  font-weight: 800;
}

.control-surface,
.summary-item,
.monitor-strip-section,
.trend-section,
.trend-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface) 92%, transparent);
  box-shadow: var(--shadow);
}

.bilibili-page.is-dark .control-surface,
.bilibili-page.is-dark .summary-item,
.bilibili-page.is-dark .monitor-strip-section,
.bilibili-page.is-dark .trend-section,
.bilibili-page.is-dark .trend-card {
  border-color: rgba(91, 111, 146, 0.58);
  background:
    linear-gradient(180deg, rgba(44, 58, 87, 0.88), rgba(30, 42, 66, 0.9));
  box-shadow: 0 18px 42px rgba(8, 13, 25, 0.24);
}

.control-surface {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
}

.add-form {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
}

.add-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.mid-input {
  width: 180px;
}

.interval-input {
  width: 120px;
}

.unit {
  margin-left: 6px;
  color: var(--muted);
  font-size: 13px;
}

.toolbar-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  color: var(--muted-strong);
  font-size: 13px;
}

.toolbar-meta span,
.layout-pills span {
  padding: 5px 9px;
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  background: var(--accent-soft);
  white-space: nowrap;
}

.bilibili-page.is-dark .toolbar-meta span,
.bilibili-page.is-dark .layout-pills span {
  border-color: rgba(131, 184, 255, 0.18);
  color: #d8e6fb;
  background: rgba(131, 184, 255, 0.12);
}

.notice-alert {
  border-radius: 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-item {
  padding: 15px 16px;
}

.summary-item span {
  display: block;
  color: var(--muted);
  font-size: 13px;
}

.summary-item strong {
  display: block;
  margin-top: 8px;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-strip-section,
.trend-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  min-width: 0;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
}

.section-heading h2 {
  margin: 0;
  color: var(--text);
  font-size: 17px;
  font-weight: 800;
}

.section-heading p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.section-heading > span {
  color: var(--muted);
  font-size: 13px;
  white-space: nowrap;
}

.user-strip {
  display: flex;
  gap: 9px;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 2px 2px 13px;
  scrollbar-color: color-mix(in srgb, var(--accent) 54%, var(--border)) color-mix(in srgb, var(--border-soft) 54%, transparent);
  scrollbar-width: auto;
}

.bilibili-page.is-dark .user-strip {
  scrollbar-color: rgba(131, 184, 255, 0.42) rgba(46, 60, 89, 0.7);
}

.user-strip::-webkit-scrollbar {
  height: 11px;
}

.user-strip::-webkit-scrollbar-track {
  border-radius: 999px;
  background: color-mix(in srgb, var(--border-soft) 62%, transparent);
}

.user-strip::-webkit-scrollbar-thumb {
  border: 2px solid color-mix(in srgb, var(--surface) 90%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 58%, var(--border));
}

.bilibili-page.is-dark .user-strip::-webkit-scrollbar-track {
  background: rgba(46, 60, 89, 0.72);
}

.bilibili-page.is-dark .user-strip::-webkit-scrollbar-thumb {
  border-color: rgba(28, 39, 62, 0.9);
  background: linear-gradient(90deg, rgba(131, 184, 255, 0.56), rgba(190, 143, 255, 0.38));
}

.monitor-user-tile {
  position: relative;
  display: flex;
  flex: 1 1 clamp(154px, 14vw, 190px);
  flex-direction: column;
  gap: 8px;
  min-width: 154px;
  max-width: 220px;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--text);
  background: var(--surface-strong);
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease,
    opacity 0.18s ease;
}

.bilibili-page.is-dark .monitor-user-tile {
  border-color: rgba(85, 104, 139, 0.54);
  background:
    linear-gradient(180deg, rgba(44, 59, 89, 0.86), rgba(34, 47, 73, 0.9));
}

.monitor-user-tile:hover,
.monitor-user-tile:focus-visible {
  border-color: color-mix(in srgb, var(--tile-accent) 70%, var(--border));
  box-shadow: 0 12px 28px color-mix(in srgb, var(--tile-accent) 18%, transparent);
  outline: none;
  transform: translateY(-1px);
}

.monitor-user-tile.selected {
  border-color: var(--tile-accent);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--tile-accent) 12%, transparent), transparent 82%),
    var(--surface-strong);
}

.bilibili-page.is-dark .monitor-user-tile.selected {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--tile-accent) 18%, transparent), transparent 86%),
    linear-gradient(180deg, rgba(48, 63, 95, 0.92), rgba(36, 49, 76, 0.94));
}

.monitor-user-tile.expanded {
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--tile-accent) 15%, transparent),
    0 12px 30px color-mix(in srgb, var(--tile-accent) 10%, transparent);
}

.monitor-user-tile.locked {
  opacity: 0.58;
}

.tile-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 7px;
  align-items: stretch;
  min-width: 0;
}

.tile-quick-actions {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
  min-width: 0;
}

.selection-mark {
  padding: 3px 7px;
  border-radius: 999px;
  color: var(--muted);
  background: color-mix(in srgb, var(--border-soft) 72%, transparent);
  font-size: 11px;
  white-space: nowrap;
}

.profile-shortcut {
  display: inline-grid;
  place-items: center;
  width: 27px;
  height: 27px;
  border: 1px solid color-mix(in srgb, var(--tile-accent) 32%, var(--border));
  border-radius: 8px;
  color: color-mix(in srgb, var(--tile-accent) 78%, var(--muted-strong));
  background: color-mix(in srgb, var(--surface-strong) 82%, transparent);
  text-decoration: none;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.profile-shortcut:hover {
  border-color: var(--tile-accent);
  background: color-mix(in srgb, var(--tile-accent) 12%, var(--surface));
  transform: translateY(-1px);
}

.profile-shortcut svg {
  width: 14px;
  height: 14px;
}

.monitor-user-tile.selected .selection-mark,
.trend-select-button.active {
  color: #fff;
  background: var(--tile-accent);
}

.trend-select-button {
  min-width: 39px;
  height: 27px;
  padding: 0 8px;
  border: 1px solid color-mix(in srgb, var(--tile-accent) 26%, var(--border));
  border-radius: 999px;
  color: var(--muted-strong);
  background: color-mix(in srgb, var(--surface-strong) 86%, transparent);
  cursor: pointer;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease;
}

.trend-select-button:not(:disabled):hover {
  border-color: var(--tile-accent);
  color: var(--tile-accent);
  background: color-mix(in srgb, var(--tile-accent) 10%, var(--surface));
}

.trend-select-button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.user-identity {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.avatar-shell {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  overflow: hidden;
  color: #175cd3;
  background: #eaf2ff;
  font-weight: 800;
}

.bilibili-page.is-dark .avatar-shell {
  color: #dbeafe;
  background:
    linear-gradient(135deg, rgba(131, 184, 255, 0.24), rgba(190, 143, 255, 0.16)),
    rgba(28, 39, 62, 0.9);
}

.avatar-shell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.identity-copy {
  min-width: 0;
}

.identity-copy strong {
  display: block;
  color: var(--text);
  font-size: 14px;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity-copy span {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-user-detail .user-identity {
  grid-template-columns: 48px minmax(0, 1fr);
}

.monitor-user-detail .avatar-shell {
  width: 48px;
  height: 48px;
}

.monitor-user-detail .identity-copy strong {
  font-size: 18px;
}

.monitor-user-detail .identity-copy span {
  font-size: 13px;
}

.tile-compact-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.tile-compact-line > strong {
  min-width: 0;
  overflow: hidden;
  font-size: 18px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.state-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  margin-top: 7px;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 11px;
  white-space: nowrap;
}

.state-pill.state-text {
  margin-top: 0;
}

.state-pill.active {
  color: #067647;
  background: #dcfae6;
}

.state-pill.paused {
  color: #344054;
  background: #f2f4f7;
}

.state-pill.error {
  color: #b42318;
  background: #fef3f2;
}

.bilibili-page.is-dark .state-pill.active {
  color: #9ee7c7;
  background: rgba(87, 212, 167, 0.14);
}

.bilibili-page.is-dark .state-pill.paused {
  color: #d2dbea;
  background: rgba(210, 219, 234, 0.12);
}

.bilibili-page.is-dark .state-pill.error {
  color: #ffb6bd;
  background: rgba(255, 142, 155, 0.14);
}

.tile-data {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(54px, 0.78fr);
  gap: 8px;
  align-items: end;
}

.tile-data span {
  display: block;
  color: var(--muted);
  font-size: 11px;
}

.tile-data strong {
  display: block;
  margin-top: 3px;
  color: var(--text);
  font-size: 17px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tile-stat-delta {
  text-align: right;
}

.tile-status-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  color: var(--muted);
  font-size: 11px;
}

.tile-status-line > span:last-child {
  min-width: 0;
  overflow: hidden;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collect-rhythm {
  display: flex;
  flex-direction: column;
}

.rhythm-bar {
  height: 5px;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--border-soft) 72%, transparent);
}

.rhythm-bar span {
  display: block;
  width: var(--tile-progress);
  min-width: 8px;
  max-width: 100%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--tile-accent), #f5b66d);
  transition: width 0.4s ease;
}

.tile-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: stretch;
  padding-top: 2px;
}

.monitor-user-detail {
  display: flex;
  flex-direction: column;
  gap: 13px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--tile-accent) 42%, var(--border));
  border-radius: 8px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--tile-accent) 10%, transparent), transparent 48%),
    var(--surface-strong);
  box-shadow: 0 14px 34px color-mix(in srgb, var(--tile-accent) 11%, transparent);
}

.bilibili-page.is-dark .monitor-user-detail {
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--tile-accent) 12%, transparent), transparent 52%),
    linear-gradient(180deg, rgba(45, 60, 90, 0.93), rgba(32, 45, 70, 0.95));
  box-shadow: 0 18px 38px rgba(7, 12, 24, 0.22);
}

.detail-card-enter-active,
.detail-card-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.detail-card-enter-from,
.detail-card-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.detail-hero {
  display: grid;
  grid-template-columns: minmax(210px, 1fr) minmax(220px, auto) auto;
  gap: 14px;
  align-items: center;
  min-width: 0;
}

.detail-primary-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(94px, 1fr));
  gap: 10px;
}

.detail-primary-metrics div,
.detail-grid > div {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface) 88%, transparent);
}

.bilibili-page.is-dark .detail-primary-metrics div,
.bilibili-page.is-dark .detail-grid > div {
  border-color: rgba(80, 98, 132, 0.56);
  background: rgba(27, 38, 60, 0.58);
}

.detail-primary-metrics span,
.detail-grid span,
.detail-rhythm > span {
  display: block;
  color: var(--muted);
  font-size: 12px;
}

.detail-primary-metrics strong {
  display: block;
  margin-top: 4px;
  color: var(--text);
  font-size: 22px;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-profile-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 11px;
  border: 1px solid color-mix(in srgb, var(--tile-accent) 36%, var(--border));
  border-radius: 8px;
  color: color-mix(in srgb, var(--tile-accent) 82%, var(--muted-strong));
  background: color-mix(in srgb, var(--surface) 86%, transparent);
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
}

.detail-profile-link:hover {
  border-color: var(--tile-accent);
  background: color-mix(in srgb, var(--tile-accent) 10%, var(--surface));
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.detail-grid strong {
  display: block;
  margin-top: 5px;
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-footer {
  display: grid;
  grid-template-columns: minmax(320px, 440px) minmax(240px, 1fr);
  gap: 12px;
  align-items: stretch;
}

.detail-rhythm {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 9px 12px;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface) 88%, transparent);
}

.detail-rhythm strong {
  color: var(--muted-strong);
  font-size: 13px;
  white-space: nowrap;
}

.interval-editor {
  display: grid;
  grid-template-columns: auto minmax(58px, 1fr) auto auto;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 7px;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface) 90%, transparent);
}

.interval-editor.compact {
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 7px;
}

.interval-editor.short {
  border-color: color-mix(in srgb, var(--warning) 55%, var(--border));
  background: color-mix(in srgb, var(--warning) 10%, var(--surface));
}

.interval-label {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}

.interval-editor.compact .interval-label,
.interval-editor.compact .unit {
  display: none;
}

.inline-interval-input {
  width: 100%;
}

.interval-editor.compact .inline-interval-input {
  min-width: 58px;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-actions.compact {
  justify-content: flex-end;
}

.icon-button {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--muted-strong);
  background: var(--surface);
  cursor: pointer;
  text-decoration: none;
}

.icon-button:hover {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--accent-soft);
}

.icon-button.profile:hover {
  color: #0e7490;
  border-color: #67e8f9;
  background: color-mix(in srgb, #67e8f9 18%, var(--surface));
}

.icon-button.danger:hover {
  color: var(--negative);
  border-color: var(--negative);
  background: color-mix(in srgb, var(--negative) 11%, var(--surface));
}

.icon-button svg {
  width: 15px;
  height: 15px;
}

.trend-section {
  min-height: 360px;
}

.layout-pills {
  display: flex;
  gap: 7px;
  color: var(--muted);
  font-size: 12px;
}

.layout-pills span.active {
  color: #fff;
  border-color: var(--accent);
  background: var(--accent);
}

.layout-pills .refresh-pill {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent) 44%, var(--border));
  background: color-mix(in srgb, var(--accent) 10%, var(--surface));
}

.layout-pills .refresh-pill.refreshing {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 18%, transparent);
}

.layout-pills .refresh-pill.done {
  color: var(--positive);
  border-color: color-mix(in srgb, var(--positive) 44%, var(--border));
  background: color-mix(in srgb, var(--positive) 10%, var(--surface));
}

.layout-pills .refresh-pill.error {
  color: var(--negative);
  border-color: color-mix(in srgb, var(--negative) 44%, var(--border));
  background: color-mix(in srgb, var(--negative) 10%, var(--surface));
}

.refresh-pill-enter-active,
.refresh-pill-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.refresh-pill-enter-from,
.refresh-pill-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}

.trend-empty {
  min-height: 300px;
}

.trend-board {
  display: grid;
  gap: 12px;
  align-items: stretch;
  min-width: 0;
}

.trend-board.chart-count-1 {
  grid-template-columns: minmax(0, 1fr);
}

.trend-board.chart-count-2,
.trend-board.chart-count-4 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.trend-board.chart-count-3 {
  grid-template-columns: minmax(0, 1.22fr) minmax(0, 0.88fr);
  grid-template-rows: repeat(2, minmax(0, auto));
}

.trend-board.chart-count-3 .trend-card:first-child {
  grid-row: 1 / span 2;
}

.trend-card {
  position: relative;
  padding: 16px;
  min-width: 0;
  overflow: hidden;
  cursor: grab;
  user-select: none;
  transition: transform 0.22s ease, opacity 0.22s ease, box-shadow 0.22s ease;
}

.trend-card:active {
  cursor: grabbing;
}

.trend-card.featured {
  padding: 18px;
}

.trend-card.dragging {
  opacity: 0.56;
  transform: scale(0.985);
  box-shadow: 0 18px 44px color-mix(in srgb, var(--accent) 18%, transparent);
}

.trend-card.drop-target {
  border-color: var(--accent);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--accent) 12%, transparent), transparent 72%),
    color-mix(in srgb, var(--surface) 92%, transparent);
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--accent) 24%, transparent),
    var(--shadow);
  transform: translateY(-2px);
}

.trend-card.drop-target::after {
  content: '释放交换';
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 4px 8px;
  border-radius: 999px;
  color: #fff;
  background: var(--accent);
  font-size: 12px;
  font-weight: 800;
  pointer-events: none;
}

.trend-card-enter-active,
.trend-card-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.trend-card-enter-from,
.trend-card-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.panel-title {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 16px;
  min-width: 0;
  margin-bottom: 12px;
}

.panel-heading-copy {
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.panel-title strong {
  display: block;
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drag-affordance {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  padding: 3px 7px;
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  color: var(--muted);
  background: color-mix(in srgb, var(--surface-strong) 72%, transparent);
  cursor: grab;
  font-size: 11px;
  font-weight: 700;
  user-select: none;
}

.trend-card.dragging .drag-affordance,
.drag-affordance:active {
  cursor: grabbing;
}

.drag-affordance svg {
  width: 13px;
  height: 13px;
}

.panel-heading-copy > span,
.panel-metric > span {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

.panel-metric {
  min-width: 74px;
  max-width: 128px;
  text-align: right;
}

.panel-metric strong {
  color: var(--text);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.up {
  color: var(--positive) !important;
}

.down {
  color: var(--negative) !important;
}

.flat {
  color: var(--muted-strong) !important;
}

.error-alert {
  position: sticky;
  bottom: 12px;
  z-index: 5;
}

.bilibili-page.is-dark :deep(.el-input__wrapper),
.bilibili-page.is-dark :deep(.el-input-number .el-input__wrapper) {
  background: rgba(22, 32, 51, 0.68);
  box-shadow: 0 0 0 1px rgba(96, 116, 152, 0.55) inset;
}

.bilibili-page.is-dark :deep(.el-input__inner) {
  color: var(--text);
}

.bilibili-page.is-dark :deep(.el-input__inner::placeholder) {
  color: rgba(174, 187, 208, 0.72);
}

.bilibili-page.is-dark :deep(.el-form-item__label) {
  color: var(--muted-strong);
}

.bilibili-page.is-dark :deep(.el-button:not(.el-button--primary)) {
  border-color: rgba(100, 120, 155, 0.58);
  color: var(--text);
  background: rgba(34, 47, 73, 0.88);
}

.bilibili-page.is-dark :deep(.el-button:not(.el-button--primary):hover) {
  border-color: rgba(131, 184, 255, 0.62);
  color: #e7f0ff;
  background: rgba(45, 61, 91, 0.96);
}

.bilibili-page.is-dark :deep(.el-button--primary) {
  --el-button-bg-color: #74adff;
  --el-button-border-color: #74adff;
  --el-button-hover-bg-color: #8fc0ff;
  --el-button-hover-border-color: #8fc0ff;
  --el-button-active-bg-color: #649fed;
  --el-button-active-border-color: #649fed;
  --el-button-text-color: #06162c;
}

.bilibili-page.is-dark :deep(.el-button--danger.is-plain) {
  --el-button-bg-color: rgba(255, 142, 155, 0.12);
  --el-button-border-color: rgba(255, 142, 155, 0.42);
  --el-button-text-color: #ffc4ca;
  --el-button-hover-bg-color: rgba(255, 142, 155, 0.2);
  --el-button-hover-border-color: rgba(255, 172, 181, 0.55);
  --el-button-hover-text-color: #ffdce0;
}

.bilibili-page.is-dark :deep(.el-alert) {
  --el-alert-bg-color: rgba(240, 199, 122, 0.12);
  --el-alert-border-color: rgba(240, 199, 122, 0.22);
  color: #f4d79c;
}

@media (max-width: 1220px) {
  .monitor-user-tile {
    min-width: 150px;
  }

  .summary-item strong {
    font-size: 21px;
  }

  .detail-hero {
    grid-template-columns: minmax(220px, 1fr) minmax(220px, 1fr);
  }

  .detail-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1060px) {
  .control-surface,
  .monitor-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .control-actions,
  .toolbar-meta {
    justify-content: flex-start;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trend-board.chart-count-2,
  .trend-board.chart-count-3,
  .trend-board.chart-count-4 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trend-board.chart-count-3 .trend-card:first-child {
    grid-row: auto;
  }

  .detail-footer {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
