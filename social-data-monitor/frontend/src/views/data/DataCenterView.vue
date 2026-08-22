<template>
  <section class="page data-center">
    <header class="page-header">
      <div>
        <h1 class="page-title">数据中心</h1>
        <p class="page-subtitle">查询场次事实、事件明细、用户汇总和采集质量；分析结论统一进入分析看板。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="refreshCurrent">立即刷新</el-button>
    </header>

    <el-card class="filter-card" shadow="never">
      <div class="filter-row">
        <label class="filter-field filter-field--room">
          <span>直播间</span>
          <el-select v-model="selectedMonitorId" placeholder="选择直播间" filterable>
            <el-option
              v-for="room in rooms"
              :key="room.id"
              :label="`${room.uname || '未知主播'} · 房间 ${room.roomId}`"
              :value="room.id"
            />
          </el-select>
        </label>
        <label class="filter-field filter-field--session">
          <span>场次</span>
          <el-select v-model="selectedSessionId" placeholder="选择场次" filterable>
            <el-option
              v-for="session in sessions"
              :key="session.id"
              :label="`#${session.id} · ${formatDateTime(session.startedAt)} · ${sessionStateLabel(session.state)}`"
              :value="session.id"
            />
          </el-select>
        </label>
        <div class="selection-summary" v-if="selectedSession">
          <strong>{{ formatInteger(selectedSession.danmakuCount) }}</strong>
          <span>弹幕</span>
          <i></i>
          <strong>{{ formatMilliYuan(selectedSession.paidAmountMilliYuan) }}</strong>
          <span>付费</span>
        </div>
      </div>
    </el-card>

    <el-card class="workspace-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="场次" name="sessions">
          <div class="section-heading">
            <div>
              <h2>最近场次</h2>
              <p>选择一场直播后，可继续查看事件、用户和数据质量。</p>
            </div>
            <span>{{ sessions.length }} 场</span>
          </div>
          <el-table
            v-loading="loading"
            :data="sessions"
            row-key="id"
            highlight-current-row
            @current-change="selectSession"
          >
            <el-table-column prop="id" label="场次" width="88">
              <template #default="scope">#{{ scope.row.id }}</template>
            </el-table-column>
            <el-table-column label="开始时间" min-width="168">
              <template #default="scope">{{ formatDateTime(scope.row.startedAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="104">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.state === 'CLOSED' ? 'info' : 'success'">
                  {{ sessionStateLabel(scope.row.state) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="弹幕" width="104" align="right">
              <template #default="scope">{{ formatInteger(scope.row.danmakuCount) }}</template>
            </el-table-column>
            <el-table-column label="礼物事件" width="112" align="right">
              <template #default="scope">{{ formatInteger(scope.row.giftEventCount) }}</template>
            </el-table-column>
            <el-table-column label="付费用户" width="112" align="right">
              <template #default="scope">{{ formatInteger(scope.row.paidUserCount) }}</template>
            </el-table-column>
            <el-table-column label="付费金额" min-width="126" align="right">
              <template #default="scope"><strong class="money">{{ formatMilliYuan(scope.row.paidAmountMilliYuan) }}</strong></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="事件明细" name="events">
          <div class="event-toolbar">
            <el-select v-model="eventFilter.kind" placeholder="全部事件" clearable>
              <el-option label="弹幕" value="DANMAKU" />
              <el-option label="礼物" value="GIFT" />
              <el-option label="醒目留言" value="SUPER_CHAT" />
              <el-option label="大航海" value="GUARD_BUY" />
            </el-select>
            <el-input v-model="eventFilter.keyword" clearable placeholder="昵称、弹幕或礼物关键词" @keyup.enter="applyEventFilter" />
            <el-input v-model="eventFilter.userUid" clearable placeholder="用户 UID" @keyup.enter="applyEventFilter" />
            <el-select v-model="eventFilter.paid">
              <el-option label="全部付费状态" value="ALL" />
              <el-option label="仅付费" value="PAID" />
              <el-option label="仅免费" value="FREE" />
            </el-select>
            <el-button type="primary" @click="applyEventFilter">筛选</el-button>
          </div>
          <el-table v-loading="eventLoading" :data="events.items" row-key="id">
            <el-table-column label="时间" min-width="164">
              <template #default="scope">{{ formatDateTime(scope.row.occurredAt) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="104">
              <template #default="scope">{{ eventKindLabel(scope.row.eventKind) }}</template>
            </el-table-column>
            <el-table-column label="用户" min-width="190">
              <template #default="scope">
                <div class="identity" :title="scope.row.senderName || identityText(scope.row.senderUid)">
                  <strong>{{ scope.row.senderName || '未解析身份' }}</strong>
                  <span>{{ identityText(scope.row.senderUid) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="内容" min-width="300">
              <template #default="scope">
                <span :title="eventContent(scope.row)">{{ eventContent(scope.row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="84" align="right">
              <template #default="scope">{{ scope.row.giftCount ?? '--' }}</template>
            </el-table-column>
            <el-table-column label="金额" min-width="116" align="right">
              <template #default="scope"><strong class="money">{{ formatMilliYuan(scope.row.paidAmountMilliYuan) }}</strong></template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <span>共 {{ events.total }} 条</span>
            <el-pagination
              v-model:current-page="eventFilter.page"
              v-model:page-size="eventFilter.size"
              layout="prev, pager, next, sizes"
              :page-sizes="[25, 50, 100]"
              :total="events.total"
              @change="loadEvents"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="用户" name="users">
          <div class="section-heading">
            <div>
              <h2>用户汇总</h2>
              <p>仅正 UID 合并为用户；未解析身份保留为独立事件记录。</p>
            </div>
            <span>最多 500 条</span>
          </div>
          <el-table v-loading="userLoading" :data="users" row-key="actorKey">
            <el-table-column label="昵称" min-width="220">
              <template #default="scope"><strong :title="scope.row.displayName">{{ scope.row.displayName || '未解析身份' }}</strong></template>
            </el-table-column>
            <el-table-column label="UID / 身份" min-width="180">
              <template #default="scope">{{ scope.row.userUid ? `UID ${scope.row.userUid}` : scope.row.actorKey }}</template>
            </el-table-column>
            <el-table-column prop="danmakuCount" label="弹幕" width="100" align="right" />
            <el-table-column prop="giftEventCount" label="礼物事件" width="110" align="right" />
            <el-table-column prop="paidEventCount" label="付费事件" width="110" align="right" />
            <el-table-column label="金额" min-width="126" align="right">
              <template #default="scope"><strong class="money">{{ formatMilliYuan(scope.row.paidAmountMilliYuan) }}</strong></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="数据质量" name="quality">
          <template v-if="selectedSession">
            <div class="quality-grid">
              <article><span>覆盖状态</span><strong>{{ coverageLabel(selectedSession.coverageStatus) }}</strong></article>
              <article><span>传输连接</span><strong>{{ selectedSession.transportSessionCount }} 个</strong></article>
              <article><span>未解析互动事件</span><strong>{{ formatInteger(selectedSession.unresolvedInteractingEventCount) }}</strong></article>
              <article><span>未解析付费事件</span><strong>{{ formatInteger(selectedSession.unresolvedPaidEventCount) }}</strong></article>
            </div>
            <el-alert
              :title="bilibiliLiveSessionCoverageCaveat(selectedSession.coverageStatus)"
              type="warning"
              :closable="false"
              show-icon
            />
          </template>
          <el-empty v-else description="请先选择场次" />
        </el-tab-pane>
      </el-tabs>

      <footer class="workspace-footer" v-if="selectedSessionId">
        <span>导出当前场次</span>
        <div>
          <el-button :icon="Download" type="primary" @click="download('xlsx')">Excel XLSX</el-button>
          <el-button :icon="Download" @click="download('danmaku')">弹幕 CSV</el-button>
          <el-button :icon="Download" @click="download('gifts')">礼物 CSV</el-button>
          <el-button :icon="TrendCharts" @click="openAnalytics">去分析看板</el-button>
        </div>
      </footer>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { Download, Refresh, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage, type TabsPaneContext } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchBilibiliLiveRooms, type BilibiliLiveRoom } from '@/api/bilibiliLive'
import {
  bilibiliLiveSessionCoverageCaveat,
  buildBilibiliLiveSessionExportUrl,
  fetchBilibiliLiveSessionEvents,
  fetchBilibiliLiveSessions,
  fetchBilibiliLiveSessionUsers,
  formatMilliYuan,
  type BilibiliLiveSessionEvent,
  type BilibiliLiveSessionEventKind,
  type BilibiliLiveSessionEventPage,
  type BilibiliLiveSessionSummary,
  type BilibiliLiveSessionUser
} from '@/api/bilibiliLiveSessions'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const eventLoading = ref(false)
const userLoading = ref(false)
const rooms = ref<BilibiliLiveRoom[]>([])
const sessions = ref<BilibiliLiveSessionSummary[]>([])
const users = ref<BilibiliLiveSessionUser[]>([])
const events = ref<BilibiliLiveSessionEventPage>({ items: [], total: 0, page: 1, size: 50 })
const selectedMonitorId = ref<number>()
const selectedSessionId = ref<number>()
const activeTab = ref('sessions')
const eventFilter = reactive<{
  kind: BilibiliLiveSessionEventKind | ''
  keyword: string
  userUid: string
  paid: 'ALL' | 'PAID' | 'FREE'
  page: number
  size: number
}>({ kind: '', keyword: '', userUid: '', paid: 'ALL', page: 1, size: 50 })

const selectedSession = computed(() => sessions.value.find(item => item.id === selectedSessionId.value))

onMounted(loadInitial)

watch(selectedMonitorId, async (value, previous) => {
  if (value != null && previous != null && value !== previous) await loadSessions()
})

watch(selectedSessionId, async (value, previous) => {
  if (value != null && value !== previous) {
    users.value = []
    events.value = { items: [], total: 0, page: 1, size: eventFilter.size }
    if (activeTab.value === 'events') await loadEvents()
    if (activeTab.value === 'users') await loadUsers()
  }
})

async function loadInitial() {
  loading.value = true
  try {
    rooms.value = await fetchBilibiliLiveRooms()
    const monitorFromRoute = numberQuery(route.query.monitorId)
    selectedMonitorId.value = rooms.value.some(room => room.id === monitorFromRoute)
      ? monitorFromRoute
      : rooms.value[0]?.id
    await loadSessions(false)
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

async function loadSessions(toggleLoading = true) {
  if (!selectedMonitorId.value) return
  if (toggleLoading) loading.value = true
  try {
    sessions.value = await fetchBilibiliLiveSessions(selectedMonitorId.value, 100)
    const sessionFromRoute = numberQuery(route.query.sessionId)
    selectedSessionId.value = sessions.value.some(item => item.id === sessionFromRoute)
      ? sessionFromRoute
      : sessions.value[0]?.id
  } catch (error) {
    notifyError(error)
  } finally {
    if (toggleLoading) loading.value = false
  }
}

async function refreshCurrent() {
  if (activeTab.value === 'sessions' || activeTab.value === 'quality') await loadSessions()
  if (activeTab.value === 'events') await loadEvents()
  if (activeTab.value === 'users') await loadUsers()
}

async function handleTabChange(tab: string | number | TabsPaneContext) {
  const name = typeof tab === 'object' ? tab.paneName : tab
  if (name === 'events') await loadEvents()
  if (name === 'users') await loadUsers()
}

async function loadEvents() {
  if (!selectedSessionId.value) return
  eventLoading.value = true
  try {
    const parsedUid = eventFilter.userUid.trim() ? Number(eventFilter.userUid) : undefined
    events.value = await fetchBilibiliLiveSessionEvents(selectedSessionId.value, {
      kind: eventFilter.kind,
      keyword: eventFilter.keyword,
      userUid: parsedUid && Number.isInteger(parsedUid) && parsedUid > 0 ? parsedUid : undefined,
      paid: eventFilter.paid === 'ALL' ? undefined : eventFilter.paid === 'PAID',
      page: eventFilter.page,
      size: eventFilter.size
    })
  } catch (error) {
    notifyError(error)
  } finally {
    eventLoading.value = false
  }
}

async function loadUsers() {
  if (!selectedSessionId.value) return
  userLoading.value = true
  try {
    users.value = await fetchBilibiliLiveSessionUsers(selectedSessionId.value, 500)
  } catch (error) {
    notifyError(error)
  } finally {
    userLoading.value = false
  }
}

function applyEventFilter() {
  eventFilter.page = 1
  void loadEvents()
}

function selectSession(session?: BilibiliLiveSessionSummary) {
  if (session) selectedSessionId.value = session.id
}

function download(category: 'xlsx' | 'danmaku' | 'gifts') {
  if (!selectedSessionId.value) return
  window.location.assign(buildBilibiliLiveSessionExportUrl(selectedSessionId.value, category))
}

function openAnalytics() {
  void router.push({ path: '/analytics', query: {
    monitorId: selectedMonitorId.value,
    sessionId: selectedSessionId.value
  } })
}

function numberQuery(value: unknown): number | undefined {
  const parsed = Number(Array.isArray(value) ? value[0] : value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}

function formatDateTime(value?: string | null) {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit'
  }).format(new Date(value))
}

function formatInteger(value?: number | null) {
  return value == null ? '--' : new Intl.NumberFormat('zh-CN').format(value)
}

function sessionStateLabel(state: string) {
  return ({ CLOSED: '已结束', OPEN: '直播中', END_PENDING: '待确认', INCOMPLETE: '边界不完整' } as Record<string, string>)[state] || state
}

function eventKindLabel(kind: string) {
  return ({ DANMAKU: '弹幕', GIFT: '礼物', SUPER_CHAT: '醒目留言', GUARD_BUY: '大航海' } as Record<string, string>)[kind] || kind
}

function coverageLabel(status: string) {
  return ({ RECEIVED_WHILE_ONLINE: '在线接收覆盖', BOUNDARY_ONLY: '仅历史边界', NO_ONLINE_COVERAGE: '无在线覆盖' } as Record<string, string>)[status] || status
}

function identityText(uid?: number | null) {
  return uid && uid > 0 ? `UID ${uid}` : '身份未解析'
}

function eventContent(event: BilibiliLiveSessionEvent) {
  if (event.messageText) return event.messageText
  if (event.giftName) return `${event.giftName}${event.giftCount ? ` × ${event.giftCount}` : ''}`
  return event.command || event.eventKind
}

function notifyError(error: unknown) {
  ElMessage.error(error instanceof Error ? error.message : '加载数据失败')
}
</script>

<style scoped>
.data-center { padding-bottom: 20px; }
.filter-card, .workspace-card { border: 1px solid #e4e9f1; border-radius: 12px; }
.filter-row { display: flex; align-items: end; gap: 14px; min-width: 0; }
.filter-field { display: grid; gap: 7px; color: #667085; font-size: 12px; }
.filter-field--room { width: 280px; }
.filter-field--session { width: 360px; }
.selection-summary { display: flex; align-items: baseline; gap: 7px; margin-left: auto; color: #667085; font-size: 12px; white-space: nowrap; }
.selection-summary strong { color: #172033; font-size: 16px; }
.selection-summary i { width: 1px; height: 18px; margin: 0 5px; background: #dfe5ee; }
.section-heading { display: flex; align-items: end; justify-content: space-between; margin: 2px 0 16px; }
.section-heading h2 { margin: 0; font-size: 17px; }
.section-heading p { margin: 5px 0 0; color: #7c8ba1; font-size: 13px; }
.section-heading > span { color: #7c8ba1; font-size: 13px; }
.event-toolbar { display: grid; grid-template-columns: 150px minmax(220px, 1fr) 150px 150px auto; gap: 10px; margin: 2px 0 16px; }
.identity { display: flex; align-items: baseline; gap: 8px; min-width: 0; white-space: nowrap; }
.identity strong { overflow: hidden; text-overflow: ellipsis; }
.identity span { flex: 0 0 auto; color: #8a9ab1; font-size: 12px; }
.money { color: #087c5a; font-variant-numeric: tabular-nums; }
.pagination-row { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; color: #7c8ba1; font-size: 13px; }
.quality-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 4px 0 18px; }
.quality-grid article { padding: 18px; border: 1px solid #e7ebf2; border-radius: 10px; background: #f8fafc; }
.quality-grid span { display: block; margin-bottom: 10px; color: #738198; font-size: 13px; }
.quality-grid strong { font-size: 20px; }
.workspace-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 18px -20px -20px; padding: 14px 20px; border-top: 1px solid #edf0f5; background: #f8fafc; color: #59677d; font-size: 13px; }
.workspace-footer > div { display: flex; flex-wrap: wrap; gap: 8px; }
:deep(.el-tabs__header) { margin-bottom: 18px; }
:deep(.el-table th.el-table__cell) { background: #f8fafc; color: #59677d; }
@media (max-width: 1100px) {
  .filter-row { align-items: stretch; flex-wrap: wrap; }
  .selection-summary { width: 100%; margin-left: 0; }
  .event-toolbar { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .quality-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 700px) {
  .filter-field--room, .filter-field--session { width: 100%; }
  .event-toolbar, .quality-grid { grid-template-columns: 1fr; }
  .workspace-footer { align-items: flex-start; flex-direction: column; }
}
</style>
