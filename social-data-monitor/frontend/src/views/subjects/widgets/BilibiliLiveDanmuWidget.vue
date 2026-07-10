<template>
  <MonitorWidgetShell
    title="直播间弹幕监控"
    :subtitle="panelSubtitle"
    :badge="statusText"
    :badge-type="statusBadgeType"
  >
    <div
      class="danmu-panel"
      @mouseenter="handleDanmuPointerActive"
      @mousemove="handleDanmuPointerActive"
      @wheel.passive="handleDanmuPointerActive"
      @mouseleave="handleDanmuPointerLeave"
    >
      <div class="data-mode-switch" role="tablist" aria-label="直播间数据视图">
        <button
          v-for="option in displayModeOptions"
          :key="option.value"
          type="button"
          class="data-mode-tab"
          :class="{ active: displayMode === option.value }"
          role="tab"
          :aria-selected="displayMode === option.value"
          @click="selectDisplayMode(option.value)"
        >
          <strong>{{ option.label }}</strong>
          <span>{{ option.description }}</span>
        </button>
      </div>

      <template v-if="displayMode === 'danmu'">
        <div class="danmu-metrics">
          <div>
            <span>弹幕数</span>
            <strong>{{ danmu.last5MinutesCount ?? '--' }}</strong>
          </div>
          <div>
            <span>点赞增量</span>
            <strong>{{ formatSigned(danmu.likeIncrement, '--') }}</strong>
          </div>
          <div>
            <span>看过人数</span>
            <strong>{{ formatCompactNumber(danmu.watchedCount) }}</strong>
          </div>
        </div>

        <div v-if="danmu.enabled && workbench.bilibiliLiveRoom" class="danmu-actions">
          <el-select v-model="protocolVersion" size="small" class="protocol-select" title="弹幕协议版本">
            <el-option v-for="option in protocolOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-button
            v-if="danmu.status === 'connected'"
            size="small"
            :loading="operating"
            @click="stopDanmaku"
          >
            停止弹幕监控
          </el-button>
          <el-button
            v-else
            size="small"
            type="primary"
            :loading="operating"
            @click="startDanmaku"
          >
            {{ startButtonText }}
          </el-button>
        </div>

        <div
          v-if="danmu.recentMessages.length"
          ref="danmuListRef"
          class="danmu-list"
        >
          <div v-for="message in danmu.recentMessages" :key="`${message.sentAt}-${message.messageText}`" class="danmu-item">
            <strong class="danmu-sender">{{ displaySenderName(message.displayName) }}</strong>
            <span class="danmu-text">{{ message.messageText }}</span>
            <em class="danmu-time">{{ formatRelativeTime(message.sentAt) }}</em>
          </div>
        </div>
        <div v-else class="danmu-empty">
          <el-tag :type="statusBadgeType" effect="light" round>
            {{ statusText }}
          </el-tag>
          <strong>{{ emptyTitle }}</strong>
          <p>{{ emptyDescription }}</p>
        </div>
      </template>

      <section v-else class="rank-compact-panel" :class="{ loading: rankLoading }">
        <div class="rank-summary-strip">
          <div>
            <span>{{ activeRankFamilyLabel }}</span>
            <strong>{{ activeRankCountText }}</strong>
          </div>
          <div>
            <span>快照</span>
            <strong>{{ rankUpdatedText }}</strong>
          </div>
        </div>

        <div v-if="rankLoading" class="rank-loading">
          <span></span>
          <strong>榜单读取中</strong>
        </div>

        <div class="rank-toolbar">
          <div class="rank-type-tabs" role="tablist" :aria-label="`${activeRankFamilyLabel}榜单类型`">
            <button
              v-for="tab in activeRankTypeTabs"
              :key="tab.key"
              type="button"
              class="rank-type-tab"
              :class="{ active: activeRankKey === tab.key }"
              @click="selectRankType(tab.key)"
            >
              {{ tab.label }}
            </button>
          </div>
          <el-button
            size="small"
            type="primary"
            plain
            :loading="rankRefreshing"
            :disabled="!roomMonitorId"
            @click="refreshRanks"
          >
            刷新
          </el-button>
        </div>

        <div v-if="!roomMonitorId" class="rank-empty">
          <strong>需要先绑定直播间监控</strong>
          <p>绑定直播间后，可以在这里查看房间观众和大航海榜单。</p>
        </div>
        <div v-else-if="!activeRankSnapshot" class="rank-empty">
          <strong>暂无{{ activeRankFamilyLabel }}快照</strong>
          <p>点击刷新榜单后，会展示在线榜、进房、周期榜或大航海排行。</p>
          <el-button size="small" type="primary" plain :loading="rankRefreshing" @click="refreshRanks">
            获取榜单
          </el-button>
        </div>
        <div v-else class="rank-view">
          <div class="rank-view-head">
            <div>
              <strong>{{ activeRankTitle }}</strong>
              <span>{{ activeRankHint }}</span>
            </div>
            <div class="rank-sort-controls">
              <el-dropdown trigger="click" @command="selectRankSort">
                <button type="button" class="rank-sort-button">
                  <span>{{ activeRankSortLabel }}</span>
                  <em>⇅</em>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="option in activeRankSortOptions"
                      :key="option.key"
                      :command="option.key"
                    >
                      <div class="rank-sort-option">
                        <strong>{{ option.label }}</strong>
                        <span>{{ option.description }}</span>
                      </div>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <button type="button" class="rank-direction-button" @click="toggleActiveRankSortDirection">
                {{ activeRankSortDirection === 'desc' ? '降序' : '升序' }}
              </button>
            </div>
          </div>

          <div v-if="activeRankEntries.length" class="rank-entry-list">
            <div
              v-for="(entry, index) in activeRankEntries"
              :key="activeRankEntryKey(entry, index)"
              class="rank-entry"
            >
              <span class="rank-no" :class="{ podium: Boolean(entry.rankNo && entry.rankNo <= 3) }">
                {{ rankNoText(entry.rankNo) }}
              </span>
              <img
                v-if="rankAvatarSrc(entry) && !isRankAvatarFailed(activeRankEntryKey(entry, index))"
                :src="rankAvatarSrc(entry)"
                alt=""
                referrerpolicy="no-referrer"
                @error="markRankAvatarFailed(activeRankEntryKey(entry, index))"
              />
              <span v-else class="rank-avatar">{{ rankAvatarFallback(entry) }}</span>
              <div class="rank-user">
                <strong>{{ entry.displayName || '匿名用户' }}</strong>
                <em>{{ activeRankEntrySubtitle(entry) }}</em>
              </div>
              <b>{{ activeRankEntryValue(entry) }}</b>
            </div>
          </div>
          <div v-else class="rank-empty compact">
            <strong>这个榜单暂无明细</strong>
            <p>{{ activeRankHint }}</p>
          </div>
        </div>
      </section>
    </div>
  </MonitorWidgetShell>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  fetchBilibiliLiveDanmakuRecent,
  fetchBilibiliLiveDanmakuStatus,
  fetchBilibiliLiveRankSummary,
  refreshBilibiliLiveRanks,
  startBilibiliLiveDanmaku,
  stopBilibiliLiveDanmaku,
  type BilibiliLiveRankEntry,
  type BilibiliLiveRankSnapshot,
  type BilibiliLiveRankSummary
} from '@/api/bilibiliLive'
import type { SubjectWorkbench } from '@/api/subjects'
import MonitorWidgetShell from '../components/MonitorWidgetShell.vue'
import { formatCompactNumber, formatRelativeTime, formatSigned } from '../formatters'

type DisplayMode = 'danmu' | 'audience' | 'guard'
type LiveRankFamily = BilibiliLiveRankSnapshot['rankFamily']
type RankSortDirection = 'desc' | 'asc'
type RankSortOption = {
  key: string
  label: string
  description: string
}

const MAX_RANK_ENTRIES = 100
const props = defineProps<{ workbench: SubjectWorkbench }>()
const emit = defineEmits<{
  refresh: []
}>()

const displayModeOptions: Array<{ value: DisplayMode; label: string; description: string }> = [
  { value: 'danmu', label: '弹幕', description: '实时流' },
  { value: 'audience', label: '房间观众', description: '榜单' },
  { value: 'guard', label: '大航海', description: '舰长' }
]
const displayMode = ref<DisplayMode>('danmu')
const liveDanmu = ref<SubjectWorkbench['danmu'] | null>(null)
const danmu = computed(() => liveDanmu.value ?? props.workbench.danmu)
const roomMonitorId = computed(() => props.workbench.bilibiliLiveRoom?.monitorId)
const danmuListRef = ref<HTMLElement | null>(null)
let pollTimer: number | undefined
let polling = false
let autoScrollTimer: number | undefined
let rankRequestId = 0
const isDanmuPointerActive = ref(false)
const rankSummary = ref<BilibiliLiveRankSummary | null>(null)
const rankLoading = ref(false)
const rankRefreshing = ref(false)
const failedRankAvatarKeys = ref<Set<string>>(new Set())
const selectedRankKeyByFamily = reactive<Record<LiveRankFamily, string>>({
  AUDIENCE: 'online_rank:contribution_rank',
  GUARD: 'guard_weekly'
})
const rankSortDirectionByKey = reactive<Record<string, RankSortDirection>>({})

const protocolOptions = [
  { label: '自动兼容', value: -1 },
  { label: 'protover=3', value: 3 },
  { label: 'protover=2', value: 2 },
  { label: 'protover=1', value: 1 },
  { label: 'protover=0', value: 0 }
]
const protocolVersion = ref(-1)
const operating = ref(false)
const liveStatus = computed(() => props.workbench.bilibiliLiveRoom?.liveStatus)
const isConnected = computed(() => danmu.value.status === 'connected')
const isLiveNow = computed(() => liveStatus.value === 1)
const latestMessageKey = computed(() => {
  const messages = danmu.value.recentMessages
  const latest = messages[messages.length - 1]
  return latest ? `${messages.length}:${latest.sentAt}:${latest.messageText}` : '0'
})

const panelSubtitle = computed(() => {
  if (displayMode.value === 'audience') return '房间观众在线榜、进房和周期贡献榜'
  if (displayMode.value === 'guard') return '舰长、提督、总督的大航海榜单'
  return 'WebSocket 实时采集弹幕速率、点赞和看过人数'
})

const statusText = computed(() => {
  if (!danmu.value.enabled) return '未启用'
  const status = danmu.value.status
  if (status === 'connected') return isLiveNow.value ? '采集中' : '监听中'
  if (status === 'waiting') return '待监听'
  if (status === 'stopped') return '已停止'
  if (status === 'missing_live_room') return '未绑定直播间'
  if (status === 'error') return '连接异常'
  return '连接中'
})

const statusBadgeType = computed<'success' | 'warning' | 'danger' | 'info' | 'primary'>(() => {
  if (!danmu.value.enabled) return 'info'
  const status = danmu.value.status
  if (status === 'connected') return 'success'
  if (status === 'error') return 'danger'
  if (status === 'missing_live_room') return 'warning'
  return 'primary'
})

const emptyTitle = computed(() => {
  if (!danmu.value.enabled) return '弹幕模块未启用'
  if (danmu.value.status === 'missing_live_room') return '需要先绑定直播间监控'
  if (danmu.value.status === 'error') return '弹幕连接暂时不可用'
  if (isConnected.value && !isLiveNow.value) return '未开播，弹幕通道监听中'
  if (danmu.value.status === 'stopped') return '弹幕监听已停止'
  if (danmu.value.status === 'waiting') return '弹幕监听待启动'
  return '等待直播间弹幕数据'
})

const emptyDescription = computed(() => {
  if (!danmu.value.enabled) return '在绑定设置中开启弹幕模块后，会自动保持直播中的房间连接。'
  if (danmu.value.status === 'missing_live_room') return '弹幕采集依赖直播间监控，请先为该用户绑定一个直播间。'
  if (danmu.value.status === 'error') return '可能是网络、B站风控或直播间状态变化导致，稍后可再次刷新查看。'
  if (isConnected.value && !isLiveNow.value) return '已连接 B站弹幕信息流。未开播时通常只有心跳、看过人数等轻量事件；直播开始后会自动显示实时弹幕。'
  if (danmu.value.status === 'stopped') return '可以手动开启未开播监听，或等待自动连接任务重新拉起。'
  if (danmu.value.status === 'waiting') return '未开播房间也支持监听；自动任务会尝试保持连接，也可以点击手动开启。'
  return '连接已建立，正在等待新的弹幕事件。'
})

const startButtonText = computed(() => {
  return liveStatus.value === 1 ? '手动开启' : '手动开启未开播监控'
})

const activeRankFamily = computed<LiveRankFamily>(() => (displayMode.value === 'guard' ? 'GUARD' : 'AUDIENCE'))
const activeRankFamilyLabel = computed(() => (activeRankFamily.value === 'GUARD' ? '大航海' : '房间观众'))
const activeRankCountText = computed(() => {
  const summary = rankSummary.value
  if (activeRankFamily.value === 'GUARD') return rankCountText(summary?.guardCount, summary?.guardCountText)
  return rankCountText(summary?.audienceCount, summary?.audienceCountText)
})
const rankUpdatedText = computed(() => rankSummary.value?.updatedAt ? formatRelativeTime(rankSummary.value.updatedAt) : '--')
const activeRankTypeTabs = computed(() => rankSnapshots(activeRankFamily.value).map((snapshot) => ({
  key: rankSnapshotKey(snapshot),
  label: rankTabLabel(snapshot)
})))
const activeRankKey = computed(() => {
  const selectedKey = selectedRankKeyByFamily[activeRankFamily.value]
  if (activeRankTypeTabs.value.some((tab) => tab.key === selectedKey)) return selectedKey
  return activeRankTypeTabs.value[0]?.key || selectedKey
})
const activeRankSnapshot = computed(() => {
  return rankSnapshots(activeRankFamily.value).find((snapshot) => rankSnapshotKey(snapshot) === activeRankKey.value)
})
const activeRankEntries = computed(() => {
  const snapshot = activeRankSnapshot.value
  return snapshot ? sortRankEntries(snapshot, snapshot.entries).slice(0, MAX_RANK_ENTRIES) : []
})
const activeRankTitle = computed(() => activeRankSnapshot.value ? rankSnapshotTitle(activeRankSnapshot.value) : '暂无榜单')
const activeRankHint = computed(() => {
  const snapshot = activeRankSnapshot.value
  if (!snapshot) return '刷新后展示最新榜单快照'
  if (snapshot.rankFamily === 'GUARD') {
    if (snapshot.rankType === 'guard_accompany') return '舰长、提督、总督的陪伴天数排行'
    return '舰长、提督、总督周期榜单'
  }
  if (snapshot.rankSwitch === 'entry_time_rank') return '按进房时间记录活跃观众'
  if (snapshot.rankType === 'online_rank') return '投喂、发弹幕均可获得贡献值'
  return '按当前周期统计的贡献值榜单'
})
const activeRankValueLabel = computed(() => {
  const snapshot = activeRankSnapshot.value
  if (!snapshot) return '数值'
  if (snapshot.rankFamily === 'GUARD') return snapshot.rankType === 'guard_accompany' ? '天数' : '等级'
  if (snapshot.rankSwitch === 'entry_time_rank') return '状态'
  return '贡献值'
})
const activeRankSortOptions = computed(() => rankSortOptions(rankSnapshots(activeRankFamily.value)))
const activeRankSortLabel = computed(() => activeRankSnapshot.value ? rankSortLabel(activeRankSnapshot.value) : '排序方式')
const activeRankSortDirection = computed(() => rankSortDirection(activeRankKey.value))

function normalizeDanmuStatus(status: string, running: boolean) {
  if (running) return 'connected'
  if (!status || status === 'NOT_STARTED') return 'waiting'
  if (status === 'ERROR') return 'error'
  if (status === 'STOPPED' || status === 'CLOSED') return 'stopped'
  return status.toLowerCase()
}

function displaySenderName(displayName?: string) {
  const normalized = displayName?.trim()
  return normalized || '游客'
}

function selectDisplayMode(mode: DisplayMode) {
  displayMode.value = mode
}

function handleDanmuPointerActive() {
  if (displayMode.value !== 'danmu') return
  isDanmuPointerActive.value = true
}

function handleDanmuPointerLeave() {
  if (displayMode.value !== 'danmu') return
  isDanmuPointerActive.value = false
  scheduleDanmuScroll('smooth')
}

function scheduleDanmuScroll(behavior: ScrollBehavior = 'smooth') {
  if (displayMode.value !== 'danmu' || isDanmuPointerActive.value) return
  if (autoScrollTimer != null) {
    window.clearTimeout(autoScrollTimer)
  }
  autoScrollTimer = window.setTimeout(() => {
    void scrollDanmuToLatest(behavior)
  }, 60)
}

async function scrollDanmuToLatest(behavior: ScrollBehavior = 'smooth') {
  if (displayMode.value !== 'danmu' || isDanmuPointerActive.value) return
  await nextTick()
  const list = danmuListRef.value
  if (!list) return
  list.scrollTo({
    top: list.scrollHeight,
    behavior
  })
}

async function pollDanmaku() {
  const monitorId = roomMonitorId.value
  if (!monitorId || !props.workbench.danmu.enabled || polling) return
  polling = true
  try {
    const [status, recentMessages] = await Promise.all([
      fetchBilibiliLiveDanmakuStatus(monitorId),
      fetchBilibiliLiveDanmakuRecent(monitorId, 60)
    ])
    liveDanmu.value = {
      ...props.workbench.danmu,
      status: normalizeDanmuStatus(status.status, status.running),
      ratePerMinute: status.ratePerMinute,
      last5MinutesCount: status.last5MinutesCount,
      likeIncrement: status.likeIncrement,
      watchedCount: status.watchedCount,
      lastMessageAt: recentMessages.length
        ? recentMessages[recentMessages.length - 1].sentAt
        : props.workbench.danmu.lastMessageAt,
      recentMessages
    }
  } catch {
    // Keep the last visible danmaku state; the parent refresh path still surfaces hard errors.
  } finally {
    polling = false
  }
}

function resetPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
  liveDanmu.value = null
  if (!roomMonitorId.value || !props.workbench.danmu.enabled) return
  void pollDanmaku()
  pollTimer = window.setInterval(() => {
    void pollDanmaku()
  }, 2000)
}

async function loadRanks(force = false) {
  const monitorId = roomMonitorId.value
  if (!monitorId) {
    rankSummary.value = null
    return
  }
  if (!force && rankSummary.value?.roomMonitorId === monitorId) return
  const requestId = ++rankRequestId
  rankLoading.value = true
  try {
    const summary = await fetchBilibiliLiveRankSummary(monitorId)
    if (requestId === rankRequestId) {
      rankSummary.value = summary
    }
  } catch (exception) {
    if (force) {
      ElMessage.error(exception instanceof Error ? exception.message : '榜单快照读取失败')
    }
  } finally {
    if (requestId === rankRequestId) {
      rankLoading.value = false
    }
  }
}

async function refreshRanks() {
  const monitorId = roomMonitorId.value
  if (!monitorId) return
  rankRefreshing.value = true
  try {
    const result = await refreshBilibiliLiveRanks(monitorId, {
      families: [activeRankFamily.value],
      types: rankRefreshTypesFromKey(activeRankKey.value),
      maxPages: 4,
      force: true
    })
    rankSummary.value = result.summary
    if (result.errors.length) {
      ElMessage.warning(`榜单已刷新 ${result.successCount} 项，${result.errors.length} 项失败`)
    } else {
      ElMessage.success('榜单已刷新')
    }
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : '榜单刷新失败')
  } finally {
    rankRefreshing.value = false
  }
}

function rankRefreshTypesFromKey(key: string) {
  const [type, rankSwitch] = key.split(':')
  return [rankSwitch || type].filter(Boolean)
}

async function startDanmaku() {
  const monitorId = props.workbench.bilibiliLiveRoom?.monitorId
  if (!monitorId) return
  operating.value = true
  try {
    const requestedProtocol = protocolVersion.value < 0 ? undefined : protocolVersion.value
    await startBilibiliLiveDanmaku(monitorId, requestedProtocol)
    if (protocolVersion.value >= 0) {
      ElMessage.success(`弹幕监控已按 protover=${protocolVersion.value} 启动`)
    } else {
      ElMessage.success('弹幕监控已按自动兼容模式启动')
    }
    emit('refresh')
    await pollDanmaku()
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : '弹幕监控启动失败')
  } finally {
    operating.value = false
  }
}

async function stopDanmaku() {
  const monitorId = props.workbench.bilibiliLiveRoom?.monitorId
  if (!monitorId) return
  operating.value = true
  try {
    await stopBilibiliLiveDanmaku(monitorId)
    ElMessage.success('弹幕监控已停止')
    emit('refresh')
    liveDanmu.value = {
      ...props.workbench.danmu,
      status: 'stopped',
      recentMessages: props.workbench.danmu.recentMessages
    }
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : '弹幕监控停止失败')
  } finally {
    operating.value = false
  }
}

function rankSnapshots(family: LiveRankFamily) {
  const latestByKey = new Map<string, BilibiliLiveRankSnapshot>()
  ;(rankSummary.value?.snapshots ?? [])
    .filter((snapshot) => snapshot.rankFamily === family)
    .forEach((snapshot) => {
      const key = rankSnapshotKey(snapshot)
      const existing = latestByKey.get(key)
      if (!existing || Date.parse(snapshot.capturedAt) >= Date.parse(existing.capturedAt)) {
        latestByKey.set(key, snapshot)
      }
    })

  return [...latestByKey.values()].sort((a, b) => rankSnapshotOrder(a) - rankSnapshotOrder(b))
}

function rankSnapshotKey(snapshot: BilibiliLiveRankSnapshot) {
  if (snapshot.rankFamily === 'GUARD') return snapshot.rankType
  return `${snapshot.rankType}:${snapshot.rankSwitch || ''}`
}

function rankSnapshotOrder(snapshot: BilibiliLiveRankSnapshot) {
  const order = [
    'online_rank:contribution_rank',
    'online_rank:entry_time_rank',
    'daily_rank:today_rank',
    'weekly_rank:current_week_rank',
    'monthly_rank:current_month_rank',
    'guard_weekly',
    'guard_monthly',
    'guard_accompany'
  ]
  const index = order.indexOf(rankSnapshotKey(snapshot))
  return index === -1 ? 99 : index
}

function rankCountText(value?: number, fallback?: string) {
  if (fallback) return fallback
  if (value == null) return '-'
  return formatCompactNumber(value)
}

function rankTabLabel(snapshot: BilibiliLiveRankSnapshot) {
  const audienceLabels: Record<string, string> = {
    'online_rank:contribution_rank': '在线榜',
    'online_rank:entry_time_rank': '进房',
    'daily_rank:today_rank': '日榜',
    'weekly_rank:current_week_rank': '周榜',
    'monthly_rank:current_month_rank': '月榜'
  }
  const guardLabels: Record<string, string> = {
    guard_weekly: '周榜',
    guard_monthly: '月榜',
    guard_accompany: '陪伴榜'
  }
  if (snapshot.rankFamily === 'GUARD') return guardLabels[snapshot.rankType] || rankSnapshotTitle(snapshot)
  return audienceLabels[rankSnapshotKey(snapshot)] || rankSnapshotTitle(snapshot)
}

function rankSnapshotTitle(snapshot: BilibiliLiveRankSnapshot) {
  const audienceTitles: Record<string, string> = {
    'online_rank:contribution_rank': '在线榜 · 贡献值',
    'online_rank:entry_time_rank': '在线榜 · 进房时间',
    'daily_rank:today_rank': '日榜 · 今日',
    'weekly_rank:current_week_rank': '周榜 · 本周',
    'monthly_rank:current_month_rank': '月榜 · 本月'
  }
  const guardTitles: Record<string, string> = {
    guard_weekly: '周榜',
    guard_monthly: '月榜',
    guard_accompany: '陪伴榜'
  }
  if (snapshot.rankFamily === 'GUARD') return guardTitles[snapshot.rankType] || snapshot.rankType
  return audienceTitles[`${snapshot.rankType}:${snapshot.rankSwitch || ''}`] || snapshot.rankType
}

function selectRankType(key: string) {
  selectedRankKeyByFamily[activeRankFamily.value] = key
}

function selectRankSort(command: string | number | object) {
  selectedRankKeyByFamily[activeRankFamily.value] = String(command)
}

function toggleActiveRankSortDirection() {
  toggleRankSortDirection(activeRankKey.value)
}

function activeRankEntryValue(entry: BilibiliLiveRankEntry) {
  const snapshot = activeRankSnapshot.value
  if (!snapshot) return '-'
  if (snapshot.rankFamily === 'GUARD' && entry.entryKind === 'EXTOP') return '上期TOP'
  return rankEntryValue(snapshot, entry)
}

function activeRankEntrySubtitle(entry: BilibiliLiveRankEntry) {
  const snapshot = activeRankSnapshot.value
  if (snapshot?.rankFamily === 'GUARD') {
    return `${guardLevelText(entry.guardLevel)}${entry.accompanyDays ? ` · 陪伴 ${entry.accompanyDays} 天` : ''}`
  }
  return entry.medalName ? `${entry.medalName}${entry.medalLevel ? ` Lv.${entry.medalLevel}` : ''}` : '无粉丝牌'
}

function activeRankEntryKey(entry: BilibiliLiveRankEntry, index: number) {
  const snapshot = activeRankSnapshot.value
  return `${snapshot?.id ?? 'rank'}-${entry.entryKind}-${entry.rankNo ?? index}-${entry.userUid ?? index}`
}

function rankAvatarSrc(entry: BilibiliLiveRankEntry) {
  return normalizeImageUrl(entry.faceUrl)
}

function isRankAvatarFailed(key: string) {
  return failedRankAvatarKeys.value.has(key)
}

function markRankAvatarFailed(key: string) {
  if (failedRankAvatarKeys.value.has(key)) return
  failedRankAvatarKeys.value = new Set([...failedRankAvatarKeys.value, key])
}

function normalizeImageUrl(url?: string) {
  const value = url?.trim()
  if (!value) return ''
  if (value.startsWith('//')) return `https:${value}`
  if (/^http:\/\//i.test(value)) return value.replace(/^http:\/\//i, 'https://')
  if (/^https:\/\//i.test(value)) return value
  if (/^[\w.-]+\//.test(value)) return `https://${value}`
  return value
}

function rankEntryValue(snapshot: BilibiliLiveRankSnapshot, entry: BilibiliLiveRankEntry) {
  if (snapshot.rankFamily === 'GUARD') {
    if (entry.accompanyDays != null) return `${entry.accompanyDays} 天`
    return guardLevelText(entry.guardLevel)
  }
  if (snapshot.rankSwitch === 'entry_time_rank') return '在房'
  if (entry.score != null) return formatCompactNumber(entry.score)
  return guardLevelText(entry.guardLevel)
}

function rankSortOptions(snapshots: BilibiliLiveRankSnapshot[]): RankSortOption[] {
  return snapshots.map((snapshot) => ({
    key: rankSnapshotKey(snapshot),
    label: rankSortLabel(snapshot),
    description: rankSortDescription(snapshot)
  }))
}

function rankSortLabel(snapshot: BilibiliLiveRankSnapshot) {
  if (snapshot.rankFamily === 'GUARD') {
    return snapshot.rankType === 'guard_accompany' ? '陪伴天数排序' : '亲密度排序'
  }
  if (snapshot.rankSwitch === 'entry_time_rank') return '进房时间排序'
  return '贡献值排序'
}

function rankSortDescription(snapshot: BilibiliLiveRankSnapshot) {
  if (snapshot.rankFamily === 'GUARD') {
    if (snapshot.rankType === 'guard_accompany') return '按上船后的陪伴天数排列'
    return '按舰长、提督、总督亲密数据排列'
  }
  if (snapshot.rankSwitch === 'entry_time_rank') return '按用户进入直播间的顺序排列'
  if (snapshot.rankType === 'daily_rank') return '按今日贡献值排列'
  if (snapshot.rankType === 'weekly_rank') return '按本周贡献值排列'
  if (snapshot.rankType === 'monthly_rank') return '按本月贡献值排列'
  return '按投喂、弹幕等贡献值排列'
}

function rankSortDirection(key: string): RankSortDirection {
  return rankSortDirectionByKey[key] || 'desc'
}

function toggleRankSortDirection(key: string) {
  rankSortDirectionByKey[key] = rankSortDirection(key) === 'desc' ? 'asc' : 'desc'
}

function sortRankEntries(snapshot: BilibiliLiveRankSnapshot, entries: BilibiliLiveRankEntry[]) {
  const direction = rankSortDirection(rankSnapshotKey(snapshot))
  return [...entries].sort((a, b) => {
    const diff = rankSortValue(snapshot, b) - rankSortValue(snapshot, a)
    if (diff !== 0) return direction === 'desc' ? diff : -diff
    const rankDiff = (a.rankNo ?? Number.MAX_SAFE_INTEGER) - (b.rankNo ?? Number.MAX_SAFE_INTEGER)
    return direction === 'desc' ? rankDiff : -rankDiff
  })
}

function rankSortValue(snapshot: BilibiliLiveRankSnapshot, entry: BilibiliLiveRankEntry) {
  if (snapshot.rankFamily === 'GUARD') {
    if (snapshot.rankType === 'guard_accompany') {
      return entry.accompanyDays ?? 0
    }
    return entry.score ?? entry.accompanyDays ?? entry.guardLevel ?? 0
  }
  if (snapshot.rankSwitch === 'entry_time_rank') {
    return entry.rankNo == null ? Number.NEGATIVE_INFINITY : -entry.rankNo
  }
  return entry.score ?? 0
}

function rankNoText(rankNo?: number) {
  if (!rankNo) return '-'
  return rankNo <= 3 ? `榜${rankNo}` : `${rankNo}`
}

function guardLevelText(level?: number) {
  if (level === 1) return '总督'
  if (level === 2) return '提督'
  if (level === 3) return '舰长'
  return '未上舰'
}

function rankAvatarFallback(entry: BilibiliLiveRankEntry) {
  return (entry.displayName || '?').slice(0, 1)
}

watch([roomMonitorId, () => props.workbench.danmu.enabled], resetPolling, { immediate: true })

watch(roomMonitorId, () => {
  rankSummary.value = null
  if (displayMode.value !== 'danmu') {
    void loadRanks(true)
  }
})

watch(displayMode, (mode) => {
  if (mode === 'danmu') {
    scheduleDanmuScroll('auto')
  } else {
    void loadRanks()
  }
})

watch(() => props.workbench.danmu, () => {
  if (!polling) {
    liveDanmu.value = null
  }
}, { deep: true })

watch(latestMessageKey, () => {
  scheduleDanmuScroll('smooth')
}, { flush: 'post', immediate: true })

onBeforeUnmount(() => {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
  }
  if (autoScrollTimer != null) {
    window.clearTimeout(autoScrollTimer)
  }
})
</script>

<style scoped>
:deep(.monitor-widget) {
  border-color: rgba(255, 201, 219, 0.32);
  background:
    radial-gradient(circle at 92% 0%, rgba(255, 190, 213, 0.08), transparent 30%),
    linear-gradient(180deg, #fff 0%, #fffdfd 100%);
  box-shadow: 0 10px 26px rgba(118, 88, 105, 0.05);
}

:deep(.monitor-widget__head) {
  border-bottom-color: rgba(230, 235, 244, 0.9);
  background:
    linear-gradient(135deg, rgba(255, 247, 250, 0.62), rgba(255, 255, 255, 0.96) 58%, rgba(247, 250, 255, 0.8));
}

:deep(.monitor-widget__title h3) {
  color: #111827;
}

:deep(.monitor-widget__title p) {
  color: #697386;
}

:deep(.monitor-widget__actions .el-tag) {
  border-color: rgba(180, 230, 202, 0.7);
  background: linear-gradient(135deg, #f7fff9, #fff9fb);
  color: #16a05d;
}

:deep(.monitor-widget__body) {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.danmu-panel {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
}

.data-mode-switch {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  padding: 4px;
  border: 1px solid rgba(229, 235, 246, 0.9);
  border-radius: 10px;
  background:
    linear-gradient(135deg, rgba(255, 248, 251, 0.95), rgba(246, 249, 255, 0.95));
}

.data-mode-tab {
  min-width: 0;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: #697386;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.data-mode-tab strong,
.data-mode-tab span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-mode-tab strong {
  color: inherit;
  font-size: 12px;
  font-weight: 900;
  line-height: 1.2;
}

.data-mode-tab span {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.2;
  opacity: 0.78;
}

.data-mode-tab.active {
  color: #ff6699;
  border-color: rgba(255, 178, 207, 0.7);
  background: linear-gradient(135deg, rgba(255, 242, 247, 0.98), rgba(255, 255, 255, 0.98));
  box-shadow: 0 7px 18px rgba(240, 103, 151, 0.08);
}

.danmu-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.danmu-metrics div {
  min-width: 0;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(231, 236, 245, 0.92);
  background:
    linear-gradient(135deg, rgba(255, 250, 252, 0.92), rgba(248, 251, 255, 0.96));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.danmu-metrics span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.danmu-metrics strong {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
  font-size: 20px;
  font-weight: 900;
}

.danmu-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.protocol-select {
  width: 132px;
}

.danmu-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  padding: 2px 6px 2px 0;
  overflow-y: auto;
  scroll-behavior: smooth;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.danmu-list::-webkit-scrollbar,
.rank-entry-list::-webkit-scrollbar {
  width: 8px;
}

.danmu-list::-webkit-scrollbar-thumb,
.rank-entry-list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: linear-gradient(180deg, #f4c7d7, #c9d6f2);
}

.danmu-list::-webkit-scrollbar-track,
.rank-entry-list::-webkit-scrollbar-track {
  border-radius: 999px;
  background: #f5f7fb;
}

.danmu-item {
  flex: 0 0 auto;
  position: relative;
  display: grid;
  grid-template-columns: minmax(86px, 116px) minmax(0, 1fr) max-content;
  align-items: start;
  column-gap: 10px;
  width: 100%;
  min-height: 0;
  padding: 9px 12px;
  border: 1px solid rgba(224, 232, 244, 0.9);
  border-radius: 9px;
  background:
    linear-gradient(135deg, rgba(255, 252, 253, 0.98), rgba(248, 251, 255, 0.98));
  box-sizing: border-box;
  overflow: hidden;
  box-shadow: 0 3px 10px rgba(78, 93, 126, 0.04);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.danmu-item::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: linear-gradient(180deg, #f2a9c3, #bdcaf0);
  opacity: 0.58;
}

.danmu-item:hover {
  border-color: rgba(244, 169, 196, 0.56);
  box-shadow: 0 7px 18px rgba(122, 94, 118, 0.08);
  transform: translateY(-1px);
}

.danmu-sender,
.danmu-text {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
  white-space: normal;
}

.danmu-sender {
  color: #6f5062;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.45;
}

.danmu-text {
  display: block;
  color: #2f3b52;
  font-size: 13px;
  line-height: 1.5;
}

.danmu-time {
  color: #a78a9b;
  font-style: normal;
  font-size: 12px;
  line-height: 1.45;
  white-space: nowrap;
}

.danmu-empty,
.rank-empty {
  flex: 1;
  min-height: 0;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  padding: 18px;
  border: 1px dashed #c8d4e4;
  border-radius: 8px;
  background: repeating-linear-gradient(135deg, #f8fafc 0, #f8fafc 12px, #f2f6fb 12px, #f2f6fb 24px);
  text-align: center;
}

.danmu-empty strong,
.rank-empty strong {
  color: #0f172a;
  font-size: 15px;
}

.danmu-empty p,
.rank-empty p {
  max-width: 300px;
  margin: 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.55;
}

.rank-compact-panel {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 10px;
}

.rank-compact-panel.loading {
  pointer-events: none;
}

.rank-loading {
  position: absolute;
  inset: 48px 10px auto;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  justify-self: center;
  width: fit-content;
  margin: 0 auto;
  padding: 7px 11px;
  border: 1px solid rgba(199, 212, 232, 0.84);
  border-radius: 999px;
  color: #64748b;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.rank-loading span {
  width: 10px;
  height: 10px;
  border: 2px solid rgba(100, 116, 139, 0.24);
  border-top-color: #ff85ad;
  border-radius: 50%;
  animation: rank-spin 0.8s linear infinite;
}

.rank-loading strong {
  font-size: 12px;
  line-height: 1;
}

.rank-summary-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.rank-summary-strip > div {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid rgba(231, 236, 245, 0.92);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 250, 252, 0.96), rgba(248, 251, 255, 0.96));
}

.rank-summary-strip span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.rank-summary-strip strong {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rank-type-tabs {
  display: flex;
  flex: 1;
  min-width: 0;
  gap: 6px;
  padding-bottom: 2px;
  overflow-x: auto;
}

.rank-type-tab {
  flex: 0 0 auto;
  min-width: 52px;
  padding: 5px 10px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 999px;
  color: #667085;
  background: rgba(255, 255, 255, 0.82);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  transition: border-color 0.18s ease, color 0.18s ease, background 0.18s ease;
}

.rank-type-tab:hover {
  color: #0f172a;
  border-color: rgba(255, 157, 193, 0.55);
}

.rank-type-tab.active {
  color: #4f6df5;
  border-color: rgba(123, 157, 255, 0.55);
  background: linear-gradient(135deg, rgba(238, 244, 255, 0.95), rgba(255, 250, 252, 0.95));
}

.rank-view {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  padding: 12px;
  border: 1px solid rgba(230, 235, 244, 0.9);
  border-radius: 10px;
  background:
    radial-gradient(circle at 100% 0%, rgba(255, 201, 219, 0.16), transparent 34%),
    linear-gradient(135deg, rgba(248, 251, 255, 0.88), rgba(255, 255, 255, 0.96));
}

.rank-view-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(230, 235, 244, 0.82);
}

.rank-view-head > div {
  min-width: 0;
}

.rank-view-head strong,
.rank-view-head span {
  display: block;
}

.rank-view-head strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-view-head span {
  color: #667085;
  font-size: 12px;
  line-height: 1.35;
}

.rank-view-head > span {
  flex: 0 0 auto;
}

.rank-sort-controls {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  padding-top: 1px;
}

.rank-sort-button,
.rank-direction-button {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  font: inherit;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.rank-sort-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 130px;
  padding: 5px 9px;
  color: #ec5fa8;
  font-size: 12px;
  font-weight: 900;
}

.rank-sort-button span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-sort-button em {
  color: rgba(236, 95, 168, 0.72);
  font-style: normal;
}

.rank-direction-button {
  padding: 5px 8px;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.rank-sort-button:hover,
.rank-direction-button:hover {
  border-color: rgba(236, 95, 168, 0.42);
  background: linear-gradient(135deg, rgba(255, 242, 247, 0.95), rgba(255, 255, 255, 0.96));
  box-shadow: 0 8px 18px rgba(236, 95, 168, 0.08);
}

.rank-sort-option {
  display: grid;
  gap: 2px;
  min-width: 136px;
}

.rank-sort-option strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 900;
  line-height: 1.25;
}

.rank-sort-option span {
  color: #667085;
  font-size: 12px;
  line-height: 1.35;
}

.rank-entry-list {
  display: grid;
  gap: 8px;
  flex: 1;
  min-height: 0;
  max-height: min(760px, 58vh);
  padding-right: 5px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.rank-entry-list::-webkit-scrollbar {
  width: 9px;
}

.rank-entry-list::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: rgba(236, 95, 168, 0.35);
  background-clip: content-box;
}

.rank-entry-list::-webkit-scrollbar-track {
  border-radius: 999px;
  background: rgba(241, 245, 249, 0.75);
}

.rank-entry {
  display: grid;
  grid-template-columns: 40px 34px minmax(0, 1fr) minmax(46px, max-content);
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 52px;
  padding: 8px 9px;
  border: 1px solid rgba(230, 235, 244, 0.72);
  border-radius: 12px;
  background:
    linear-gradient(135deg, rgba(255, 247, 250, 0.7), transparent 46%),
    rgba(255, 255, 255, 0.88);
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.035);
}

.rank-no {
  color: #0f172a;
  font-size: 12px;
  font-weight: 900;
}

.rank-no.podium {
  padding: 4px 7px;
  border-radius: 999px;
  color: #fff;
  text-align: center;
  background: linear-gradient(135deg, #ff8f98, #ec5fa8);
  box-shadow: 0 8px 18px rgba(236, 95, 168, 0.14);
}

.rank-entry img,
.rank-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
}

.rank-entry img {
  object-fit: cover;
}

.rank-avatar {
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, #6d8dff, #ff8fbd);
  font-size: 12px;
  font-weight: 900;
}

.rank-user {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.rank-user strong,
.rank-user em,
.rank-entry b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-user strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 900;
  line-height: 1.25;
}

.rank-user em {
  color: #7a8699;
  font-size: 11px;
  font-style: normal;
  line-height: 1.3;
}

.rank-entry b {
  color: #0f9960;
  font-size: 13px;
  font-weight: 900;
  text-align: right;
}

.rank-empty.compact {
  border-style: solid;
  background: rgba(248, 250, 252, 0.9);
}

@keyframes rank-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
