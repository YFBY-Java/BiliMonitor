<template>
  <section class="session-panel" @click.stop>
    <div class="session-panel-head">
      <div>
        <h4>场次统计与导出</h4>
        <p>{{ roomName || `监控房间 ${monitorId}` }} · 最近 {{ sessions.length }} 场</p>
      </div>
      <div class="session-refresh-controls">
        <span>每</span>
        <el-input-number
          v-model="refreshIntervalSeconds"
          :min="1"
          :max="3600"
          :step="1"
          controls-position="right"
          size="small"
          aria-label="场次统计自动刷新秒数"
        />
        <span>秒刷新</span>
        <el-button
          :icon="Refresh"
          :loading="sessionsLoading || autoRefreshInFlight"
          plain
          @click="refreshNow"
        >
          立即刷新
        </el-button>
      </div>
    </div>

    <el-alert
      class="boundary-alert"
      type="warning"
      show-icon
      :closable="false"
      :title="bilibiliLiveSessionCaptureScopeCaveat()"
    />

    <el-alert
      v-if="errorMessage"
      class="session-error"
      type="error"
      show-icon
      closable
      :title="errorMessage"
      @close="errorMessage = ''"
    />

    <el-skeleton v-if="sessionsLoading && sessions.length === 0" :rows="5" animated />
    <el-empty v-else-if="sessions.length === 0" description="暂无可用场次记录" />

    <div v-else class="session-layout">
      <aside class="session-list" aria-label="最近直播场次">
        <button
          v-for="session in sessions"
          :key="session.id"
          type="button"
          class="session-list-item"
          :class="{ active: selectedSessionId === session.id }"
          @click="selectSession(session.id)"
        >
          <span class="session-state" :class="sessionStateClass(session.state)">
            {{ sessionStateText(session.state) }}
          </span>
          <strong>{{ formatDateTime(session.startedAt) }}</strong>
          <small>{{ formatDuration(session.startedAt, session.endedAt, session.state) }}</small>
          <em>{{ metricCount(session.danmakuCount) }} 弹幕 · {{ metricCount(session.giftEventCount) }} 礼物事件</em>
          <small class="coverage-label">{{ coverageStatusText(session.coverageStatus) }}</small>
        </button>
      </aside>

      <main class="session-detail">
        <el-skeleton v-if="detailLoading && !selectedSession" :rows="6" animated />
        <template v-else-if="selectedSession">
          <div class="session-detail-head">
            <div>
              <span class="session-state" :class="sessionStateClass(selectedSession.state)">
                {{ sessionStateText(selectedSession.state) }}
              </span>
              <h5>{{ formatDateTime(selectedSession.startedAt) }} 场次</h5>
              <p>
                场次 #{{ selectedSession.id }} · UID {{ selectedSession.uid }} · 房间 {{ selectedSession.roomId }}
              </p>
            </div>
            <span v-if="detailLoading" class="loading-label">明细加载中</span>
          </div>

          <el-alert
            v-if="isBilibiliLiveSessionEndPending(selectedSession.state)"
            class="end-pending-alert"
            type="warning"
            show-icon
            :closable="false"
            :title="`等待结束确认：${bilibiliLiveSessionEndPendingCaveat()}`"
          />

          <el-alert
            v-if="detailUnavailable"
            class="detail-boundary-alert"
            type="info"
            show-icon
            :closable="false"
            title="该场次只有可恢复的边界或汇总记录，部分事件和身份记录不可用。"
          />

          <el-alert
            class="coverage-alert"
            :type="selectedSession.coverageStatus === 'RECEIVED_WHILE_ONLINE' ? 'info' : 'warning'"
            show-icon
            :closable="false"
            :title="`${coverageStatusText(selectedSession.coverageStatus)}：${bilibiliLiveSessionCoverageCaveat(selectedSession.coverageStatus)}`"
          />

          <div class="session-time-grid">
            <div>
              <span>开始</span>
              <strong>{{ formatDateTime(selectedSession.startedAt) }}</strong>
              <small>{{ sourceText(selectedSession.startSource) }}</small>
            </div>
            <div>
              <span>结束</span>
              <strong>{{ selectedSession.endedAt
                ? formatDateTime(selectedSession.endedAt)
                : bilibiliLiveSessionEndFallback(selectedSession.state, selectedSession.endedAt) }}</strong>
              <small>{{ sourceText(selectedSession.endSource) }}</small>
            </div>
            <div>
              <span>时长</span>
              <strong>{{ formatDuration(selectedSession.startedAt, selectedSession.endedAt, selectedSession.state) }}</strong>
              <small>首末事件 {{ eventRangeText(selectedSession) }}</small>
            </div>
          </div>

          <div class="session-metrics">
            <div><span>弹幕数</span><strong>{{ metricCount(selectedSession.danmakuCount) }}</strong></div>
            <div><span>礼物事件</span><strong>{{ metricCount(selectedSession.giftEventCount) }}</strong></div>
            <div><span>礼物数量</span><strong>{{ metricCount(selectedSession.giftCount) }}</strong></div>
            <div><span>免费礼物</span><strong>{{ metricCount(selectedSession.freeGiftCount) }}</strong></div>
            <div><span>{{ BILIBILI_LIVE_SESSION_IDENTITY_METRIC_LABELS.giftSender }}</span><strong>{{ metricCount(selectedSession.giftSenderCount) }}</strong></div>
            <div><span>{{ BILIBILI_LIVE_SESSION_IDENTITY_METRIC_LABELS.paidUser }}</span><strong>{{ metricCount(selectedSession.paidUserCount) }}</strong></div>
            <div><span>{{ BILIBILI_LIVE_SESSION_IDENTITY_METRIC_LABELS.interactingUser }}</span><strong>{{ metricCount(selectedSession.interactingUserCount) }}</strong></div>
            <div><span>未解析互动事件</span><strong>{{ metricCount(selectedSession.unresolvedInteractingEventCount) }}</strong></div>
            <div><span>未解析送礼事件</span><strong>{{ metricCount(selectedSession.unresolvedGiftEventCount) }}</strong></div>
            <div><span>未解析付费事件</span><strong>{{ metricCount(selectedSession.unresolvedPaidEventCount) }}</strong></div>
            <div class="money-metric"><span>消费金额</span><strong>{{ formatMilliYuan(selectedSession.paidAmountMilliYuan) }}</strong></div>
          </div>
          <p class="identity-metric-caveat">{{ bilibiliLiveSessionIdentityMetricsCaveat() }}</p>

          <div class="export-actions">
            <span>导出当前场次</span>
            <el-button
              v-for="action in exportActions"
              :key="action.category"
              size="small"
              :type="action.category === 'xlsx' ? 'primary' : 'default'"
              :plain="action.category !== 'xlsx'"
              :icon="Download"
              @click="downloadSession(action.category)"
            >
              {{ action.label }}
            </el-button>
          </div>

          <section class="top-users">
            <div class="top-users-head">
              <div>
                <h5>Top 身份记录</h5>
                <p>按服务端统计顺序展示{{ bilibiliLiveSessionIdentityListCaveat() }}</p>
              </div>
              <span>{{ users.length }} 条</span>
            </div>

            <el-empty v-if="!detailLoading && users.length === 0" description="该场次暂无身份记录" />
            <div v-else class="top-user-list">
              <article v-for="(user, index) in users" :key="user.actorKey">
                <span class="user-rank">{{ index + 1 }}</span>
                <strong class="session-user-name" :title="userName(user)">{{ userName(user) }}</strong>
                <small class="session-user-uid" :title="userIdentityText(user)">{{ userIdentityText(user) }}</small>
                <div class="user-stats">
                  <span>{{ user.danmakuCount ?? 0 }} 弹幕</span>
                  <span>{{ user.giftCount ?? 0 }} 礼物</span>
                  <span>{{ user.paidEventCount ?? 0 }} 次付费</span>
                </div>
                <strong class="user-money">{{ formatMilliYuan(user.paidAmountMilliYuan) }}</strong>
              </article>
            </div>
          </section>
        </template>
      </main>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Download, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  BILIBILI_LIVE_SESSION_IDENTITY_METRIC_LABELS,
  bilibiliLiveSessionEndFallback,
  bilibiliLiveSessionCoverageCaveat,
  bilibiliLiveSessionCaptureScopeCaveat,
  bilibiliLiveSessionEndPendingCaveat,
  bilibiliLiveSessionIdentityListCaveat,
  bilibiliLiveSessionIdentityMetricsCaveat,
  buildBilibiliLiveSessionExportUrl,
  fetchBilibiliLiveSession,
  fetchBilibiliLiveSessions,
  fetchBilibiliLiveSessionUsers,
  formatMilliYuan,
  isBilibiliLiveSessionEndPending,
  isBilibiliLiveSessionDurationUnknown,
  type BilibiliLiveSessionCoverageStatus,
  type BilibiliLiveSessionDetail,
  type BilibiliLiveSessionExportCategory,
  type BilibiliLiveSessionSummary,
  type BilibiliLiveSessionUser
} from '@/api/bilibiliLiveSessions'
import { useSessionAutoRefresh } from '@/views/bilibili-live/useSessionAutoRefresh'

const props = defineProps<{
  monitorId: number
  roomName?: string
}>()

const exportActions: Array<{ category: BilibiliLiveSessionExportCategory; label: string }> = [
  { category: 'xlsx', label: 'Excel XLSX' },
  { category: 'danmaku', label: '弹幕 CSV' },
  { category: 'gifts', label: '礼物 CSV' },
  { category: 'users', label: '用户 CSV' },
  { category: 'all', label: '全部 ZIP' }
]

const sessions = ref<BilibiliLiveSessionSummary[]>([])
const selectedSessionId = ref<number>()
const sessionDetail = ref<BilibiliLiveSessionDetail>()
const users = ref<BilibiliLiveSessionUser[]>([])
const sessionsLoading = ref(false)
const detailLoading = ref(false)
const detailUnavailable = ref(false)
const errorMessage = ref('')
const refreshIntervalSeconds = ref(10)
let listGeneration = 0
let detailGeneration = 0

const selectedSession = computed<BilibiliLiveSessionDetail | BilibiliLiveSessionSummary | undefined>(() =>
  sessionDetail.value ?? sessions.value.find(session => session.id === selectedSessionId.value)
)

const sessionAutoRefresh = useSessionAutoRefresh({
  refresh: () => loadSessions(true),
  intervalSeconds: refreshIntervalSeconds.value
})
const autoRefreshInFlight = sessionAutoRefresh.inFlight

watch(() => props.monitorId, () => void loadSessions(false), { immediate: true })
watch(refreshIntervalSeconds, value => sessionAutoRefresh.setIntervalSeconds(value))
onBeforeUnmount(sessionAutoRefresh.stop)
sessionAutoRefresh.start()

async function loadSessions(preserveSelection: boolean) {
  const generation = ++listGeneration
  const previousSelection = preserveSelection ? selectedSessionId.value : undefined
  detailGeneration += 1
  sessionsLoading.value = true
  detailLoading.value = false
  errorMessage.value = ''
  detailUnavailable.value = false
  if (!preserveSelection) {
    sessionDetail.value = undefined
    users.value = []
    selectedSessionId.value = undefined
  }
  try {
    const nextSessions = await fetchBilibiliLiveSessions(props.monitorId, 20)
    if (generation !== listGeneration) return
    sessions.value = nextSessions
    const nextSelection = previousSelection != null && nextSessions.some(session => session.id === previousSelection)
      ? previousSelection
      : nextSessions[0]?.id
    if (nextSelection != null) {
      await selectSession(nextSelection, preserveSelection && nextSelection === previousSelection)
    } else {
      selectedSessionId.value = undefined
      sessionDetail.value = undefined
      users.value = []
    }
  } catch (error) {
    if (generation === listGeneration) {
      if (!preserveSelection) sessions.value = []
      errorMessage.value = readableError(error, '场次记录加载失败')
    }
  } finally {
    if (generation === listGeneration) sessionsLoading.value = false
  }
}

async function selectSession(sessionId: number, preserveCurrent = false) {
  const generation = ++detailGeneration
  selectedSessionId.value = sessionId
  if (!preserveCurrent) {
    sessionDetail.value = undefined
    users.value = []
  }
  detailUnavailable.value = false
  detailLoading.value = true
  errorMessage.value = ''

  const [detailResult, usersResult] = await Promise.allSettled([
    fetchBilibiliLiveSession(sessionId),
    fetchBilibiliLiveSessionUsers(sessionId, 100)
  ])
  if (generation !== detailGeneration) return

  if (detailResult.status === 'fulfilled') {
    sessionDetail.value = detailResult.value
  } else {
    detailUnavailable.value = true
  }
  if (usersResult.status === 'fulfilled') {
    users.value = usersResult.value
  } else {
    detailUnavailable.value = true
  }
  if (detailResult.status === 'rejected' && usersResult.status === 'rejected') {
    errorMessage.value = '该历史场次暂无可恢复的受支持事件或身份记录。'
  }
  detailLoading.value = false
}

async function refreshNow() {
  await sessionAutoRefresh.refreshNow()
}

function downloadSession(category: BilibiliLiveSessionExportCategory) {
  if (!selectedSessionId.value) return
  const relativeUrl = buildBilibiliLiveSessionExportUrl(selectedSessionId.value, category)
  const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  const anchor = document.createElement('a')
  anchor.href = `${apiBaseUrl}${relativeUrl}`
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  const message = category === 'xlsx'
    ? 'Excel 工作簿已开始下载'
    : category === 'all'
      ? '统一 ZIP 已开始下载'
      : 'CSV 已开始下载'
  ElMessage.success(message)
}

function sessionStateText(state?: string | null) {
  const normalized = state?.trim().toUpperCase()
  if (isBilibiliLiveSessionEndPending(normalized)) return '等待结束确认'
  if (['OPEN', 'ACTIVE', 'RUNNING', 'LIVE'].includes(normalized || '')) return '进行中'
  if (['CLOSED', 'ENDED', 'FINISHED'].includes(normalized || '')) return '已结束'
  if (['BOUNDARY', 'HISTORICAL_BOUNDARY', 'INCOMPLETE'].includes(normalized || '')) return '历史边界'
  return state || '未知状态'
}

function sessionStateClass(state?: string | null) {
  const normalized = state?.trim().toUpperCase()
  if (isBilibiliLiveSessionEndPending(normalized)) return 'is-end-pending'
  if (['OPEN', 'ACTIVE', 'RUNNING', 'LIVE'].includes(normalized || '')) return 'is-live'
  if (['BOUNDARY', 'HISTORICAL_BOUNDARY', 'INCOMPLETE'].includes(normalized || '')) return 'is-boundary'
  return 'is-ended'
}

function formatDateTime(value?: string | null) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date)
}

function formatDuration(startedAt?: string | null, endedAt?: string | null, state?: string | null) {
  if (isBilibiliLiveSessionDurationUnknown(state, endedAt)) return '--'
  if (!startedAt) return '--'
  const start = Date.parse(startedAt)
  const end = endedAt ? Date.parse(endedAt) : Date.now()
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return '--'
  const totalSeconds = Math.floor((end - start) / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (hours > 0) return `${hours}小时 ${minutes}分 ${seconds}秒`
  if (minutes > 0) return `${minutes}分 ${seconds}秒`
  return `${seconds}秒`
}

function sourceText(source?: string | null) {
  if (!source) return '来源未知'
  return `来源 ${source}`
}

function eventRangeText(session: BilibiliLiveSessionSummary) {
  if (!session.firstEventAt && !session.lastEventAt) return '--'
  return `${formatDateTime(session.firstEventAt)} ～ ${formatDateTime(session.lastEventAt)}`
}

function userName(user: BilibiliLiveSessionUser) {
  return user.displayName?.trim() || (user.userUid ? `UID ${user.userUid}` : '匿名用户')
}

function userIdentityText(user: BilibiliLiveSessionUser) {
  return user.identityQuality === 'VERIFIED_UID'
    ? `UID ${user.userUid}`
    : `身份未解析 · ${user.actorKey}`
}

function coverageStatusText(status?: BilibiliLiveSessionCoverageStatus | null) {
  if (status === 'BOUNDARY_ONLY') return '仅历史边界'
  if (status === 'RECEIVED_WHILE_ONLINE') return '在线接收覆盖'
  return '无在线覆盖'
}

function metricCount(value?: number | null) {
  return value == null ? '--' : value
}

function readableError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
</script>

<style scoped>
.session-panel {
  margin-top: 18px;
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
}

.session-panel-head,
.session-detail-head,
.top-users-head,
.export-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.session-panel h4,
.session-panel h5,
.session-panel p {
  margin: 0;
}

.session-panel-head h4 {
  color: #172033;
  font-size: 18px;
}

.session-refresh-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.session-refresh-controls :deep(.el-input-number) {
  width: 92px;
}

.session-panel-head p,
.session-detail-head p,
.top-users-head p {
  margin-top: 5px;
  color: #718096;
  font-size: 12px;
}

.boundary-alert,
.session-error,
.detail-boundary-alert,
.coverage-alert {
  margin-top: 14px;
}

.end-pending-alert {
  margin-top: 14px;
  --el-alert-bg-color: rgba(254, 243, 199, 0.9);
}

.session-layout {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 720px;
  overflow-y: auto;
}

.session-list-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 5px 10px;
  width: 100%;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 12px;
  color: #334155;
  text-align: left;
  background: rgba(248, 250, 252, 0.88);
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.session-list-item:hover,
.session-list-item.active {
  border-color: rgba(18, 168, 120, 0.55);
  background: rgba(236, 253, 245, 0.92);
  transform: translateY(-1px);
}

.session-list-item strong {
  align-self: center;
  font-size: 13px;
}

.session-list-item small,
.session-list-item em {
  grid-column: 1 / -1;
  color: #64748b;
  font-size: 11px;
  font-style: normal;
}

.session-list-item .coverage-label {
  color: #b45309;
  font-weight: 700;
}

.session-state {
  display: inline-flex;
  align-items: center;
  width: max-content;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.session-state.is-live {
  color: #047857;
  background: #d1fae5;
}

.session-state.is-ended {
  color: #475569;
  background: #e2e8f0;
}

.session-state.is-end-pending {
  color: #a16207;
  background: #fef3c7;
}

.session-state.is-boundary {
  color: #b45309;
  background: #fef3c7;
}

.session-detail {
  min-width: 0;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.75);
}

.session-detail-head > div {
  display: grid;
  gap: 6px;
}

.session-detail-head h5,
.top-users-head h5 {
  color: #1e293b;
  font-size: 15px;
}

.loading-label {
  color: #0f766e;
  font-size: 12px;
}

.session-time-grid,
.session-metrics {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.session-time-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.session-metrics {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.session-time-grid > div,
.session-metrics > div {
  display: grid;
  gap: 5px;
  padding: 12px;
  border-radius: 11px;
  background: rgba(241, 245, 249, 0.88);
}

.session-time-grid span,
.session-metrics span {
  color: #64748b;
  font-size: 11px;
}

.session-time-grid strong,
.session-metrics strong {
  color: #1e293b;
  font-size: 14px;
}

.session-time-grid small {
  overflow: hidden;
  color: #94a3b8;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-metrics .money-metric {
  background: rgba(236, 253, 245, 0.92);
}

.identity-metric-caveat {
  margin-top: 8px !important;
  color: #64748b;
  font-size: 11px;
}

.money-metric strong,
.user-money {
  color: #047857 !important;
}

.export-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: 16px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(239, 246, 255, 0.72);
}

.export-actions > span {
  margin-right: auto;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.top-users {
  margin-top: 18px;
}

.top-users-head > span {
  color: #64748b;
  font-size: 12px;
}

.top-user-list {
  display: grid;
  gap: 7px;
  max-height: 420px;
  margin-top: 12px;
  overflow-y: auto;
}

.top-user-list article {
  display: grid;
  grid-template-columns: 28px 300px 160px minmax(240px, 1fr) 132px;
  grid-template-areas: "rank name uid stats money";
  align-items: center;
  gap: 4px 18px;
  padding: 9px 11px;
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.9);
}

.user-rank {
  grid-area: rank;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  color: #64748b;
  background: #e2e8f0;
  font-size: 11px;
  font-weight: 800;
}

.session-user-name {
  grid-area: name;
  color: #334155;
  font-size: 12px;
}

.session-user-uid {
  grid-area: uid;
  color: #94a3b8;
  font-size: 10px;
}

.session-user-name,
.session-user-uid {
  min-width: 0;
  max-width: 100%;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-stats {
  color: #94a3b8;
  font-size: 10px;
}

.user-stats {
  grid-area: stats;
  display: grid;
  grid-template-columns: 76px 64px 76px;
  justify-content: flex-end;
  gap: 12px;
  white-space: nowrap;
}

.user-stats span {
  text-align: right;
}

.user-money {
  grid-area: money;
  text-align: right;
  font-size: 12px;
  white-space: nowrap;
}

.user-rank,
.user-stats,
.user-money {
  font-variant-numeric: tabular-nums;
}

:global(.live-page.is-dark) .session-panel,
:global(.live-page.is-dark) .session-detail {
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.62);
}

:global(.live-page.is-dark) .session-panel h4,
:global(.live-page.is-dark) .session-panel h5,
:global(.live-page.is-dark) .session-time-grid strong,
:global(.live-page.is-dark) .session-metrics strong {
  color: #e2e8f0;
}

:global(.live-page.is-dark) .session-list-item,
:global(.live-page.is-dark) .session-time-grid > div,
:global(.live-page.is-dark) .session-metrics > div,
:global(.live-page.is-dark) .top-user-list article {
  border-color: rgba(148, 163, 184, 0.14);
  color: #cbd5e1;
  background: rgba(30, 41, 59, 0.72);
}

:global(.live-page.is-dark) .session-list-item.active {
  border-color: rgba(52, 211, 153, 0.55);
  background: rgba(6, 78, 59, 0.42);
}

:global(.live-page.is-dark) .session-user-name {
  color: #cbd5e1;
}

@media (max-width: 980px) {
  .session-layout {
    grid-template-columns: 1fr;
  }

  .session-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    max-height: 300px;
  }

  .session-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .session-panel {
    padding: 13px;
  }

  .session-panel-head,
  .session-detail-head,
  .top-users-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .session-refresh-controls {
    flex-wrap: wrap;
  }

  .session-list,
  .session-time-grid,
  .session-metrics {
    grid-template-columns: 1fr;
  }

}
</style>
