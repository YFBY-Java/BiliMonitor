<template>
  <section class="page live-page" :class="themeClass">
    <div class="page-header live-header">
      <div>
        <h1 class="page-title">B站直播间监控</h1>
        <p class="page-subtitle">匿名公开接口采集直播状态、在线热度和房间信息。</p>
      </div>
    </div>

    <section class="live-control-surface">
      <el-form class="add-form" :inline="true" @submit.prevent>
        <el-form-item label="添加类型">
          <el-segmented
            v-model="addMode"
            :options="[
              { label: '房间号', value: 'roomId' },
              { label: 'UID', value: 'uid' }
            ]"
            :disabled="adding"
          />
        </el-form-item>
        <el-form-item :label="addMode === 'roomId' ? '房间号' : 'UID'">
          <el-input
            v-model="addForm.identifier"
            class="live-id-input"
            inputmode="numeric"
            :placeholder="addMode === 'roomId' ? '例如 7734200' : '例如 401742377'"
            clearable
            :disabled="adding"
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
            :disabled="adding"
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
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        <div class="toolbar-meta">
          <span>{{ summary?.totalRooms ?? rooms.length }} 个房间</span>
          <span>{{ summary?.liveRooms ?? liveRooms.length }} 个直播中</span>
          <span>{{ summary?.errorRooms ?? errorRooms.length }} 个异常</span>
          <span>{{ summary?.todayLiveStarts ?? 0 }} 个今日开播</span>
        </div>
      </div>
    </section>

    <el-alert
      v-if="shortIntervalNotice"
      class="notice-alert"
      type="warning"
      title="已允许短间隔采集；短间隔会受全局请求节流和失败退避保护，建议只用于少量直播间的临时观察。"
      show-icon
      :closable="false"
    />

    <section class="live-summary-grid">
      <article class="summary-item">
        <span>直播中房间</span>
        <strong>{{ summary?.liveRooms ?? liveRooms.length }}</strong>
      </article>
      <article class="summary-item">
        <span>总在线/热度</span>
        <strong>{{ formatShortCount(summary?.totalOnlineCount ?? totalOnlineCount) }}</strong>
      </article>
      <article class="summary-item">
        <span>今日开播次数</span>
        <strong>{{ summary?.todayLiveStarts ?? 0 }}</strong>
      </article>
      <article class="summary-item">
        <span>最近状态变化</span>
        <strong>{{ latestEventText }}</strong>
      </article>
    </section>

    <el-empty v-if="!loading && rooms.length === 0" description="暂无监控直播间">
      <el-button type="primary" :icon="Plus" @click="focusAddInput">添加房间号或 UID</el-button>
    </el-empty>

    <template v-else>
      <section class="room-strip-section">
        <div class="section-heading">
          <div>
            <h2>监控直播间</h2>
            <p>横向卡片展示直播状态、热度和采集节奏；最多选择 4 个进入趋势图。</p>
          </div>
          <span>{{ selectedTrendRoomIds.length }}/{{ MAX_SELECTED_TRENDS }} 已选</span>
        </div>

        <div class="room-strip" aria-label="监控直播间横向列表">
          <article
            v-for="room in rooms"
            :key="room.id"
            class="room-tile"
            :class="{
              selected: selectedTrendRoomIds.includes(room.id),
              expanded: expandedRoomId === room.id,
              paused: room.monitorStatus === 'PAUSED',
              error: !!room.lastErrorType,
              live: room.liveStatus === 1
            }"
            :style="{ '--collect-progress': `${collectProgress(room)}%`, '--room-accent': roomAccent(room) }"
            role="button"
            tabindex="0"
            @click="toggleExpandedRoom(room)"
            @keydown.enter.prevent="toggleExpandedRoom(room)"
            @keydown.space.prevent="toggleExpandedRoom(room)"
          >
            <div class="tile-head">
              <div class="avatar-shell">
                <img v-if="room.faceUrl" :src="room.faceUrl" :alt="room.uname" referrerpolicy="no-referrer" />
                <span v-else>{{ avatarFallback(room) }}</span>
              </div>
              <div class="tile-copy">
                <strong :title="room.uname">{{ room.uname }}</strong>
                <span>UID {{ room.uid }} · 房间 {{ room.roomId }}</span>
              </div>
              <span class="status-pill" :class="statusClass(room)">{{ statusText(room) }}</span>
            </div>

            <p class="room-title" :title="room.title || '未获取到标题'">{{ room.title || '未获取到房间标题' }}</p>

            <div class="tile-metrics">
              <div>
                <span>在线/热度</span>
                <strong>{{ formatShortCount(room.onlineCount) }}</strong>
              </div>
              <div>
                <span>变化</span>
                <strong :class="deltaClass(room.onlineDelta)">{{ formatDelta(room.onlineDelta) }}</strong>
              </div>
            </div>

            <div class="tile-rhythm">
              <span>{{ nextCollectBrief(room) }}</span>
              <div class="rhythm-bar"><i /></div>
            </div>

            <div class="tile-actions" @click.stop>
              <el-tooltip content="打开直播间">
                <a
                  class="icon-action"
                  :href="liveRoomUrl(room)"
                  target="_blank"
                  rel="noreferrer"
                  title="打开直播间"
                  @click.stop
                >
                  <el-icon><LinkIcon /></el-icon>
                </a>
              </el-tooltip>
              <el-tooltip :content="selectedTrendRoomIds.includes(room.id) ? '移出趋势图' : '加入趋势图'">
                <button
                  class="trend-action"
                  :class="{ active: selectedTrendRoomIds.includes(room.id) }"
                  type="button"
                  :disabled="isTrendLocked(room)"
                  @click.stop="toggleTrendRoom(room)"
                >
                  {{ selectedTrendRoomIds.includes(room.id) ? '已选' : isTrendLocked(room) ? '上限' : '趋势' }}
                </button>
              </el-tooltip>
            </div>
          </article>
        </div>

        <Transition name="detail-card">
          <article v-if="expandedRoom" class="room-detail" :style="{ '--room-accent': roomAccent(expandedRoom) }">
            <div class="detail-main">
              <div class="detail-title-row">
                <div class="detail-heading-group">
                  <div class="detail-preview">
                    <img
                      v-if="detailImageUrl(expandedRoom)"
                      :src="detailImageUrl(expandedRoom)"
                      :alt="expandedRoom.title || expandedRoom.uname"
                      referrerpolicy="no-referrer"
                    />
                    <div v-else class="preview-fallback">
                      <el-icon><VideoPlay /></el-icon>
                      <span>暂无封面</span>
                    </div>
                    <span class="status-pill large" :class="statusClass(expandedRoom)">{{ statusText(expandedRoom) }}</span>
                  </div>
                  <div class="detail-title-copy">
                    <h3>{{ expandedRoom.uname }}</h3>
                    <p>{{ expandedRoom.title || '未获取到房间标题' }}</p>
                    <div class="detail-title-meta">
                      <span>UID {{ expandedRoom.uid }}</span>
                      <span>房间 {{ expandedRoom.roomId }}</span>
                      <span v-if="expandedRoom.shortId">短号 {{ expandedRoom.shortId }}</span>
                      <span>{{ areaText(expandedRoom) }}</span>
                    </div>
                  </div>
                </div>
                <div class="detail-actions" @click.stop>
                  <a class="detail-link" :href="liveRoomUrl(expandedRoom)" target="_blank" rel="noreferrer">
                    <el-icon><LinkIcon /></el-icon>
                    打开直播间
                  </a>
                  <el-button :icon="Refresh" :loading="refreshingRoomIds.includes(expandedRoom.id)" @click="refreshRoom(expandedRoom)">立即刷新</el-button>
                  <el-switch
                    :model-value="expandedRoom.monitorStatus === 'ACTIVE'"
                    inline-prompt
                    active-text="启用"
                    inactive-text="停用"
                    @change="toggleExpandedRoomStatus"
                  />
                  <el-button :icon="Delete" plain type="danger" @click="removeRoom(expandedRoom)">删除</el-button>
                </div>
              </div>

              <el-alert
                v-if="expandedRoom.lastErrorType"
                class="detail-alert"
                type="error"
                show-icon
                :closable="false"
                :title="`${expandedRoom.lastErrorType}: ${expandedRoom.lastErrorMessage || '采集失败'}`"
              />

              <div class="detail-grid">
                <div><span>在线/热度</span><strong>{{ formatShortCount(expandedRoom.onlineCount) }}</strong></div>
                <div><span>关注数</span><strong>{{ formatShortCount(expandedRoom.attentionCount) }}</strong></div>
                <div><span>开播时间</span><strong>{{ formatDateTime(expandedRoom.liveTime) }}</strong></div>
                <div><span>开播时长</span><strong>{{ liveDurationText(expandedRoom) }}</strong></div>
                <div><span>最后成功</span><strong>{{ formatDateTime(expandedRoom.lastSuccessAt) }}</strong></div>
                <div><span>下次采集</span><strong>{{ expandedRoom.monitorStatus === 'PAUSED' ? '已停用' : formatDateTime(expandedRoom.nextCollectAt) }}</strong></div>
                <div><span>来源接口</span><strong>{{ expandedRoom.sourceEndpoint || '-' }}</strong></div>
                <div><span>退避到期</span><strong>{{ formatDateTime(expandedRoom.backoffUntil) }}</strong></div>
              </div>

              <div class="detail-bottom">
                <div class="interval-editor" :class="{ dirty: isIntervalDirty(expandedRoom) }" @click.stop>
                  <span>{{ isIntervalDirty(expandedRoom) ? '采集间隔（未保存）' : '采集间隔' }}</span>
                  <el-input-number
                    :model-value="intervalDraft(expandedRoom)"
                    :min="MIN_INTERVAL_SECONDS"
                    :max="MAX_INTERVAL_SECONDS"
                    :step="intervalStep(intervalDraft(expandedRoom))"
                    :controls="false"
                    @update:model-value="setIntervalDraft(expandedRoom, $event)"
                  />
                  <span class="unit">秒</span>
                  <el-button
                    type="primary"
                    plain
                    :icon="Check"
                    :disabled="!isIntervalDirty(expandedRoom)"
                    :loading="savingRoomIds.includes(expandedRoom.id)"
                    @click="saveInterval(expandedRoom)"
                  >
                    {{ isIntervalDirty(expandedRoom) ? '保存修改' : '已保存' }}
                  </el-button>
                </div>

                <div class="data-health">
                  <span>数据完整性</span>
                  <div>
                    <em
                      v-for="item in missingFieldItems(expandedRoom)"
                      :key="item.label"
                      :class="{ missing: !item.ok }"
                    >
                      {{ item.ok ? item.label : `${item.label}缺失` }}
                    </em>
                  </div>
                </div>
              </div>

            </div>
          </article>
        </Transition>
      </section>

      <section class="live-trend-section">
        <div class="section-heading">
          <div>
            <h2>近 24 小时趋势</h2>
            <p>{{ trendHelpText }}</p>
          </div>
          <span v-if="trendRefreshing" class="refresh-pill">趋势刷新中</span>
        </div>

        <el-empty
          v-if="selectedTrendRooms.length === 0"
          class="trend-empty"
          description="选择直播间后查看在线趋势"
        />
        <div v-else class="trend-grid" :class="`count-${selectedTrendRooms.length}`">
          <article
            v-for="(room, index) in selectedTrendRooms"
            :key="room.id"
            class="trend-card"
            :class="{ featured: selectedTrendRooms.length === 1 || (selectedTrendRooms.length === 3 && index === 0) }"
          >
            <div class="trend-card-title">
              <div>
                <strong>{{ room.uname }}</strong>
                <span>房间 {{ room.roomId }} · {{ points(room).length }} 个点</span>
              </div>
              <div>
                <strong>{{ formatShortCount(room.onlineCount) }}</strong>
                <span :class="deltaClass(room.onlineDelta)">{{ formatDelta(room.onlineDelta) }}</span>
              </div>
            </div>
            <TrendChart
              :labels="chartLabels(room)"
              :timestamps="chartTimestamps(room)"
              :series="[{ name: '在线/热度', values: chartValues(room), color: roomAccent(room) }]"
              :height="chartHeight(index)"
              :theme="theme"
              :accent-color="roomAccent(room)"
              value-format="precise-compact"
            />
            <p v-if="points(room).length < 2" class="chart-note">至少需要 2 次采集形成趋势。</p>
          </article>
        </div>
      </section>

      <section v-if="expandedRoom" class="rank-panel" @click.stop>
        <div class="rank-panel-head">
          <div>
            <h4>房间观众与大航海</h4>
            <p>公开榜单数据，手动刷新后保存最新快照。</p>
          </div>
          <div class="rank-actions">
            <span v-if="rankSummary(expandedRoom)?.updatedAt">更新 {{ relativeTime(rankSummary(expandedRoom)?.updatedAt) }}</span>
            <el-button
              size="small"
              type="primary"
              plain
              :loading="rankRefreshingRoomIds.includes(expandedRoom.id)"
              @click="refreshRanks(expandedRoom)"
            >
              刷新榜单
            </el-button>
          </div>
        </div>

        <el-empty
          v-if="!rankLoadingRoomIds.includes(expandedRoom.id) && !rankSummary(expandedRoom)?.snapshots.length"
          description="暂无榜单快照，点击刷新榜单获取房间观众和大航海数据"
        />
        <div v-else class="rank-board">
          <section
            v-for="family in rankFamilyTabs(expandedRoom)"
            :key="family.value"
            class="rank-board-column"
          >
            <div class="rank-column-head">
              <div>
                <h5>{{ family.label }}</h5>
                <p>{{ rankFamilyDescription(family.value) }}</p>
              </div>
              <span>{{ family.count }}</span>
            </div>

            <div
              v-if="rankTypeTabsForFamily(expandedRoom, family.value).length"
              class="rank-type-tabs"
              role="tablist"
              :aria-label="`${family.label}榜单周期`"
            >
              <button
                v-for="tab in rankTypeTabsForFamily(expandedRoom, family.value)"
                :key="tab.key"
                type="button"
                class="rank-type-tab"
                :class="{ active: activeRankKeyForFamily(expandedRoom, family.value) === tab.key }"
                @click="selectRankTypeForFamily(family.value, tab.key)"
              >
                {{ tab.label }}
              </button>
            </div>

            <el-empty
              v-if="!activeRankSnapshotForFamily(expandedRoom, family.value)"
              :description="`暂无${family.label}快照`"
            />
            <div v-else class="rank-view">
              <div class="rank-view-head">
                <div>
                  <strong>{{ activeRankTitleForFamily(expandedRoom, family.value) }}</strong>
                  <span>{{ activeRankHintForFamily(expandedRoom, family.value) }}</span>
                </div>
                <div class="rank-sort-controls">
                  <el-dropdown trigger="click" @command="(command: string | number | object) => selectRankSortForFamily(family.value, command)">
                    <button type="button" class="rank-sort-button">
                      <span>{{ activeRankSortLabelForFamily(expandedRoom, family.value) }}</span>
                      <em>⇅</em>
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          v-for="option in rankSortOptionsForFamily(expandedRoom, family.value)"
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
                  <button
                    type="button"
                    class="rank-direction-button"
                    @click="toggleRankSortDirectionForFamily(expandedRoom, family.value)"
                  >
                    {{ activeRankSortDirectionForFamily(expandedRoom, family.value) === 'desc' ? '降序' : '升序' }}
                  </button>
                </div>
              </div>
              <div class="rank-entry-list rank-entry-list-large">
                <div
                  v-for="(entry, index) in activeRankEntriesForFamily(expandedRoom, family.value)"
                  :key="activeRankEntryKeyForFamily(expandedRoom, family.value, entry, index)"
                  class="rank-entry rank-entry-large"
                >
                  <span class="rank-no" :class="{ podium: Boolean(entry.rankNo && entry.rankNo <= 3) }">
                    {{ rankNoText(entry.rankNo) }}
                  </span>
                  <img
                    v-if="rankAvatarSrc(entry) && !isRankAvatarFailed(activeRankEntryKeyForFamily(expandedRoom, family.value, entry, index))"
                    :src="rankAvatarSrc(entry)"
                    alt=""
                    referrerpolicy="no-referrer"
                    @error="markRankAvatarFailed(activeRankEntryKeyForFamily(expandedRoom, family.value, entry, index))"
                  />
                  <span v-else class="rank-avatar">{{ (entry.displayName || '?').slice(0, 1) }}</span>
                  <div class="rank-user">
                    <strong>{{ entry.displayName || '匿名用户' }}</strong>
                    <em>{{ activeRankEntrySubtitleForFamily(expandedRoom, family.value, entry) }}</em>
                  </div>
                  <b>{{ activeRankEntryValueForFamily(expandedRoom, family.value, entry) }}</b>
                </div>
              </div>
            </div>
          </section>
        </div>
      </section>

      <section class="event-section">
        <div class="section-heading">
          <div>
            <h2>最近状态事件</h2>
            <p>记录开播、下播、轮播、标题变化和采集异常。</p>
          </div>
        </div>
        <el-empty v-if="events.length === 0" description="暂无状态事件" />
        <div v-else class="event-list">
          <article v-for="event in events" :key="event.id">
            <span>{{ eventText(event) }}</span>
            <strong>{{ relativeTime(event.occurredAt) }}</strong>
          </article>
        </div>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Check, Delete, Link as LinkIcon, Moon, Plus, Refresh, Sunny, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import TrendChart from '@/components/charts/TrendChart.vue'
import {
  addBilibiliLiveRoom,
  deleteBilibiliLiveRoom,
  fetchBilibiliLiveEvents,
  fetchBilibiliLiveRankSummary,
  fetchBilibiliLiveRooms,
  fetchBilibiliLiveSummary,
  fetchBilibiliLiveTrends,
  refreshBilibiliLiveRoom,
  refreshBilibiliLiveRanks,
  updateBilibiliLiveRoom,
  type BilibiliLiveRankEntry,
  type BilibiliLiveRankSnapshot,
  type BilibiliLiveRankSummary,
  type BilibiliLiveRoom,
  type BilibiliLiveStatusEvent,
  type BilibiliLiveSummary,
  type BilibiliLiveTrendPoint,
  type RefreshBilibiliLiveRanksBody
} from '@/api/bilibiliLive'

type MonitorTheme = 'light' | 'dark'
type AddMode = 'roomId' | 'uid'
type LiveRankFamily = 'AUDIENCE' | 'GUARD'
type RankSortDirection = 'desc' | 'asc'
type RankSortOption = {
  key: string
  label: string
  description: string
}

const MIN_INTERVAL_SECONDS = 1
const MAX_INTERVAL_SECONDS = 2_592_000
const SHORT_INTERVAL_WARNING_SECONDS = 120
const MAX_SELECTED_TRENDS = 4
const MAX_RANK_ENTRIES = 100
const THEME_STORAGE_KEY = 'bilibili-live-monitor-theme'
const ROOM_COLORS = ['#12a878', '#2f80ed', '#a970ff', '#ec5fa8', '#f97316', '#64748b']

const loading = ref(false)
const adding = ref(false)
const trendRefreshing = ref(false)
const apiError = ref('')
const rooms = ref<BilibiliLiveRoom[]>([])
const summary = ref<BilibiliLiveSummary>()
const events = ref<BilibiliLiveStatusEvent[]>([])
const expandedRoomId = ref<number>()
const selectedTrendRoomIds = ref<number[]>([])
const trendPointsByRoomId = ref<Record<number, BilibiliLiveTrendPoint[]>>({})
const rankSummariesByRoomId = ref<Record<number, BilibiliLiveRankSummary>>({})
const refreshingRoomIds = ref<number[]>([])
const savingRoomIds = ref<number[]>([])
const rankLoadingRoomIds = ref<number[]>([])
const rankRefreshingRoomIds = ref<number[]>([])
const intervalDrafts = reactive<Record<number, number>>({})
const dirtyIntervalRoomIds = ref<number[]>([])
const now = ref(Date.now())
const theme = ref<MonitorTheme>('light')
const addMode = ref<AddMode>('roomId')
const selectedRankKeyByFamily = reactive<Record<LiveRankFamily, string>>({
  AUDIENCE: 'online_rank:contribution_rank',
  GUARD: 'guard_weekly'
})
const rankSortDirectionByKey = reactive<Record<string, RankSortDirection>>({})
const failedRankAvatarKeys = ref<Set<string>>(new Set())
const addForm = reactive({
  identifier: '',
  intervalSeconds: 300
})

let clockTimer: number | undefined
let trendRefreshTimer: number | undefined

const isDarkTheme = computed({
  get: () => theme.value === 'dark',
  set: (value: boolean) => {
    theme.value = value ? 'dark' : 'light'
  }
})
const themeClass = computed(() => (theme.value === 'dark' ? 'is-dark' : 'is-light'))
const liveRooms = computed(() => rooms.value.filter((room) => room.liveStatus === 1))
const errorRooms = computed(() => rooms.value.filter((room) => room.lastErrorType))
const totalOnlineCount = computed(() =>
  liveRooms.value.reduce((sum, room) => sum + (room.onlineCount ?? 0), 0)
)
const expandedRoom = computed(() => rooms.value.find((room) => room.id === expandedRoomId.value))
const selectedTrendRooms = computed(() =>
  selectedTrendRoomIds.value
    .map((id) => rooms.value.find((room) => room.id === id))
    .filter((room): room is BilibiliLiveRoom => Boolean(room))
)
const latestEventText = computed(() => {
  const event = summary.value?.latestEvent ?? events.value[0]
  if (!event) return '24 小时内无变化'
  return relativeTime(event.occurredAt)
})
const shortIntervalNotice = computed(() =>
  addForm.intervalSeconds < SHORT_INTERVAL_WARNING_SECONDS ||
  rooms.value.some((room) => intervalDraft(room) < SHORT_INTERVAL_WARNING_SECONDS)
)
const trendHelpText = computed(() => {
  if (!selectedTrendRooms.value.length) return '点击直播间卡片中的趋势按钮，最多同时查看 4 个房间。'
  if (selectedTrendRooms.value.length === 1) return '单个房间使用大面积图表，适合观察连续变化。'
  if (selectedTrendRooms.value.length === 2) return '两个房间左右并排，适合对比开播热度。'
  if (selectedTrendRooms.value.length === 3) return '三个房间采用主次布局，第一个房间获得更大图表区域。'
  return '四个房间使用 2x2 网格，适合宽屏监控。'
})

watch(theme, (value) => {
  localStorage.setItem(THEME_STORAGE_KEY, value)
  applyDocumentTheme(value)
})

async function loadAll() {
  loading.value = true
  apiError.value = ''
  try {
    const [roomList, nextSummary, nextEvents] = await Promise.all([
      fetchBilibiliLiveRooms(),
      fetchBilibiliLiveSummary(),
      fetchBilibiliLiveEvents(20)
    ])
    rooms.value = roomList
    summary.value = nextSummary
    events.value = nextEvents
    syncIntervalDrafts()
    normalizeSelection()
    if (selectedTrendRoomIds.value.length) {
      queueTrendRefresh(0)
    }
    if (expandedRoomId.value) {
      void loadRankSummary(expandedRoomId.value)
    }
  } catch (error) {
    apiError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function submitAdd() {
  const value = Number(addForm.identifier)
  if (!Number.isInteger(value) || value <= 0) {
    ElMessage.warning(`请输入有效${addMode.value === 'roomId' ? '房间号' : 'UID'}`)
    return
  }
  adding.value = true
  apiError.value = ''
  try {
    const room = await addBilibiliLiveRoom({
      [addMode.value]: value,
      intervalSeconds: addForm.intervalSeconds
    })
    addForm.identifier = ''
    await loadAll()
    expandedRoomId.value = room.id
    if (!selectedTrendRoomIds.value.includes(room.id) && selectedTrendRoomIds.value.length < MAX_SELECTED_TRENDS) {
      selectedTrendRoomIds.value = [...selectedTrendRoomIds.value, room.id]
      queueTrendRefresh()
    }
    ElMessage.success('已添加直播间监控')
  } catch (error) {
    apiError.value = errorMessage(error)
  } finally {
    adding.value = false
  }
}

async function refreshRoom(room: BilibiliLiveRoom) {
  setIdState(refreshingRoomIds, room.id, true)
  apiError.value = ''
  try {
    await refreshBilibiliLiveRoom(room.id)
    await loadAll()
    if (selectedTrendRoomIds.value.includes(room.id)) {
      queueTrendRefresh(0)
    }
    ElMessage.success('已刷新直播间')
  } catch (error) {
    apiError.value = errorMessage(error)
  } finally {
    setIdState(refreshingRoomIds, room.id, false)
  }
}

async function loadRankSummary(roomId: number) {
  setIdState(rankLoadingRoomIds, roomId, true)
  try {
    const summary = await fetchBilibiliLiveRankSummary(roomId)
    rankSummariesByRoomId.value = {
      ...rankSummariesByRoomId.value,
      [roomId]: summary
    }
  } catch (error) {
    const message = errorMessage(error)
    if (!message.includes('暂无')) {
      apiError.value = message
    }
  } finally {
    setIdState(rankLoadingRoomIds, roomId, false)
  }
}

async function refreshRanks(room: BilibiliLiveRoom) {
  setIdState(rankRefreshingRoomIds, room.id, true)
  apiError.value = ''
  try {
    const families: LiveRankFamily[] = ['AUDIENCE', 'GUARD']
    const results = []
    for (const family of families) {
      results.push(await refreshBilibiliLiveRanks(room.id, rankRefreshRequestForFamily(room, family)))
    }
    const result = {
      summary: results[results.length - 1].summary,
      successCount: results.reduce((sum, item) => sum + item.successCount, 0),
      errors: results.flatMap((item) => item.errors ?? [])
    }
    rankSummariesByRoomId.value = {
      ...rankSummariesByRoomId.value,
      [room.id]: result.summary
    }
    if (result.errors?.length) {
      ElMessage.warning(`榜单已部分刷新：${result.successCount} 个成功，${result.errors.length} 个失败`)
    } else {
      ElMessage.success('已刷新房间观众与大航海榜单')
    }
  } catch (error) {
    apiError.value = errorMessage(error)
  } finally {
    setIdState(rankRefreshingRoomIds, room.id, false)
  }
}

function rankRefreshRequestForFamily(
  room: BilibiliLiveRoom,
  family: LiveRankFamily
): RefreshBilibiliLiveRanksBody {
  const snapshot = activeRankSnapshotForFamily(room, family)
  const key = snapshot ? rankSnapshotKey(snapshot) : activeRankKeyForFamily(room, family)
  return {
    families: [family],
    types: rankRefreshTypesFromKey(key),
    maxPages: 4,
    force: true
  }
}

function rankRefreshTypesFromKey(key: string) {
  const [type, rankSwitch] = key.split(':')
  return [rankSwitch || type].filter(Boolean)
}

async function toggleMonitor(room: BilibiliLiveRoom, enabled: boolean) {
  apiError.value = ''
  try {
    await updateBilibiliLiveRoom(room.id, { enabled })
    await loadAll()
  } catch (error) {
    apiError.value = errorMessage(error)
  }
}

function toggleExpandedRoomStatus(value: string | number | boolean) {
  if (!expandedRoom.value) return
  toggleMonitor(expandedRoom.value, value === true)
}

async function saveInterval(room: BilibiliLiveRoom) {
  const intervalSeconds = clampInterval(intervalDraft(room))
  if (intervalSeconds === room.intervalSeconds) return
  setIdState(savingRoomIds, room.id, true)
  apiError.value = ''
  try {
    await updateBilibiliLiveRoom(room.id, { intervalSeconds })
    intervalDrafts[room.id] = intervalSeconds
    setIntervalDirty(room.id, false)
    await loadAll()
    ElMessage.success(`已更新采集间隔为 ${formatInterval(intervalSeconds)}`)
  } catch (error) {
    apiError.value = errorMessage(error)
  } finally {
    setIdState(savingRoomIds, room.id, false)
  }
}

async function removeRoom(room: BilibiliLiveRoom) {
  await ElMessageBox.confirm(`删除 ${room.uname} 的直播间监控？`, '删除监控直播间', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  apiError.value = ''
  try {
    await deleteBilibiliLiveRoom(room.id)
    selectedTrendRoomIds.value = selectedTrendRoomIds.value.filter((id) => id !== room.id)
    if (expandedRoomId.value === room.id) {
      expandedRoomId.value = undefined
    }
    const nextTrendPoints = { ...trendPointsByRoomId.value }
    delete nextTrendPoints[room.id]
    trendPointsByRoomId.value = nextTrendPoints
    const nextRankSummaries = { ...rankSummariesByRoomId.value }
    delete nextRankSummaries[room.id]
    rankSummariesByRoomId.value = nextRankSummaries
    await loadAll()
    ElMessage.success('已删除直播间监控')
  } catch (error) {
    apiError.value = errorMessage(error)
  }
}

function toggleExpandedRoom(room: BilibiliLiveRoom) {
  const nextId = expandedRoomId.value === room.id ? undefined : room.id
  expandedRoomId.value = nextId
  if (nextId) {
    void loadRankSummary(nextId)
  }
}

function toggleTrendRoom(room: BilibiliLiveRoom) {
  if (selectedTrendRoomIds.value.includes(room.id)) {
    selectedTrendRoomIds.value = selectedTrendRoomIds.value.filter((id) => id !== room.id)
    queueTrendRefresh()
    return
  }
  if (selectedTrendRoomIds.value.length >= MAX_SELECTED_TRENDS) {
    ElMessage.warning('最多选择 4 个直播间查看趋势')
    return
  }
  selectedTrendRoomIds.value = [...selectedTrendRoomIds.value, room.id]
  queueTrendRefresh()
}

function queueTrendRefresh(delay = 180) {
  if (trendRefreshTimer) {
    window.clearTimeout(trendRefreshTimer)
  }
  if (!selectedTrendRoomIds.value.length) {
    trendRefreshing.value = false
    return
  }
  trendRefreshTimer = window.setTimeout(() => {
    trendRefreshTimer = undefined
    refreshSelectedTrendData()
  }, delay)
}

async function refreshSelectedTrendData() {
  if (!selectedTrendRoomIds.value.length) return
  trendRefreshing.value = true
  try {
    const trends = await fetchBilibiliLiveTrends(selectedTrendRoomIds.value, 500)
    const nextPoints = { ...trendPointsByRoomId.value }
    trends.forEach((trend) => {
      nextPoints[trend.room.id] = trend.points
    })
    trendPointsByRoomId.value = nextPoints
  } catch (error) {
    apiError.value = errorMessage(error)
  } finally {
    window.setTimeout(() => {
      trendRefreshing.value = false
    }, 500)
  }
}

function normalizeSelection() {
  const liveIds = new Set(rooms.value.map((room) => room.id))
  selectedTrendRoomIds.value = selectedTrendRoomIds.value.filter((id) => liveIds.has(id)).slice(0, MAX_SELECTED_TRENDS)
  if (!expandedRoomId.value || !liveIds.has(expandedRoomId.value)) {
    expandedRoomId.value = rooms.value[0]?.id
  }
  if (!selectedTrendRoomIds.value.length) {
    const defaults = [...rooms.value]
      .sort((a, b) => (b.liveStatus === 1 ? 1 : 0) - (a.liveStatus === 1 ? 1 : 0))
      .slice(0, 1)
      .map((room) => room.id)
    selectedTrendRoomIds.value = defaults
  }
}

function syncIntervalDrafts() {
  const liveIds = new Set(rooms.value.map((room) => room.id))
  Object.keys(intervalDrafts).forEach((id) => {
    if (!liveIds.has(Number(id))) {
      delete intervalDrafts[Number(id)]
    }
  })
  dirtyIntervalRoomIds.value = dirtyIntervalRoomIds.value.filter((id) => liveIds.has(id))
  rooms.value.forEach((room) => {
    if (!isIntervalDirty(room)) {
      intervalDrafts[room.id] = room.intervalSeconds
    }
  })
}

function intervalDraft(room: BilibiliLiveRoom) {
  return intervalDrafts[room.id] ?? room.intervalSeconds
}

function setIntervalDraft(room: BilibiliLiveRoom, value: number | undefined) {
  const nextValue = clampInterval(value ?? MIN_INTERVAL_SECONDS)
  intervalDrafts[room.id] = nextValue
  setIntervalDirty(room.id, nextValue !== room.intervalSeconds)
}

function setIntervalDirty(roomId: number, dirty: boolean) {
  const nextIds = new Set(dirtyIntervalRoomIds.value)
  if (dirty) nextIds.add(roomId)
  else nextIds.delete(roomId)
  dirtyIntervalRoomIds.value = [...nextIds]
}

function isIntervalDirty(room: BilibiliLiveRoom) {
  return dirtyIntervalRoomIds.value.includes(room.id) && intervalDraft(room) !== room.intervalSeconds
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

function setIdState(target: { value: number[] }, id: number, enabled: boolean) {
  const nextIds = new Set(target.value)
  if (enabled) nextIds.add(id)
  else nextIds.delete(id)
  target.value = [...nextIds]
}

function points(room: BilibiliLiveRoom) {
  const refreshedPoints = trendPointsByRoomId.value[room.id]
  if (refreshedPoints?.length) return refreshedPoints
  if (room.recentTrend?.length) return room.recentTrend
  if (room.onlineCount == null) return []
  return [{
    roomId: room.roomId,
    uid: room.uid,
    capturedAt: room.lastSnapshotAt || new Date().toISOString(),
    liveStatus: room.liveStatus,
    onlineCount: room.onlineCount,
    attentionCount: room.attentionCount,
    sourceEndpoint: room.sourceEndpoint
  }]
}

function chartLabels(room: BilibiliLiveRoom) {
  return points(room).map((point) => formatChartTime(point.capturedAt))
}

function chartTimestamps(room: BilibiliLiveRoom) {
  return points(room).map((point) => point.capturedAt)
}

function chartValues(room: BilibiliLiveRoom) {
  return points(room).map((point) => point.onlineCount ?? 0)
}

function chartHeight(index: number) {
  const count = selectedTrendRooms.value.length
  if (count === 1) return 420
  if (count === 2) return 320
  if (count === 3) return index === 0 ? 420 : 200
  return 240
}

function collectProgress(room: BilibiliLiveRoom) {
  if (room.monitorStatus === 'PAUSED') return 0
  if (room.lastErrorType) return 100
  if (!room.nextCollectAt) return 0
  const end = Date.parse(room.nextCollectAt)
  const startSource = room.lastSnapshotAt || room.lastSuccessAt
  let start = startSource ? Date.parse(startSource) : end - room.intervalSeconds * 1000
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return 100
  return Math.min(100, Math.max(4, ((now.value - start) / (end - start)) * 100))
}

function nextCollectBrief(room: BilibiliLiveRoom) {
  if (room.monitorStatus === 'PAUSED') return '已停用'
  if (room.lastErrorType) return room.backoffUntil ? `退避至 ${formatTime(room.backoffUntil)}` : '异常退避'
  if (!room.nextCollectAt) return '未排程'
  const diff = Date.parse(room.nextCollectAt) - now.value
  if (diff <= 0) return '待采集'
  if (diff < 60_000) return `${Math.ceil(diff / 1000)} 秒后`
  if (diff < 3_600_000) return `${Math.ceil(diff / 60_000)} 分后`
  if (diff < 86_400_000) return `${Math.ceil(diff / 3_600_000)} 小时后`
  return `${Math.ceil(diff / 86_400_000)} 天后`
}

function statusText(room: BilibiliLiveRoom) {
  if (room.monitorStatus === 'PAUSED') return '暂停'
  if (room.lastErrorType) return '异常'
  if (room.liveStatus === 1) return 'LIVE'
  if (room.liveStatus === 2) return '轮播'
  return '未开播'
}

function statusClass(room: BilibiliLiveRoom) {
  return {
    live: room.monitorStatus === 'ACTIVE' && !room.lastErrorType && room.liveStatus === 1,
    round: room.monitorStatus === 'ACTIVE' && !room.lastErrorType && room.liveStatus === 2,
    offline: room.monitorStatus === 'ACTIVE' && !room.lastErrorType && room.liveStatus === 0,
    paused: room.monitorStatus === 'PAUSED',
    error: !!room.lastErrorType
  }
}

function isTrendLocked(room: BilibiliLiveRoom) {
  return !selectedTrendRoomIds.value.includes(room.id) && selectedTrendRoomIds.value.length >= MAX_SELECTED_TRENDS
}

function roomAccent(room: BilibiliLiveRoom) {
  const selectedIndex = selectedTrendRoomIds.value.indexOf(room.id)
  const index = selectedIndex >= 0 ? selectedIndex : rooms.value.findIndex((item) => item.id === room.id)
  return ROOM_COLORS[Math.max(0, index) % ROOM_COLORS.length]
}

function detailImageUrl(room: BilibiliLiveRoom) {
  return room.keyframeUrl || room.coverUrl
}

function liveRoomUrl(room: BilibiliLiveRoom) {
  return `https://live.bilibili.com/${room.roomId}`
}

function areaText(room: BilibiliLiveRoom) {
  if (room.parentAreaName && room.areaName) return `${room.parentAreaName} / ${room.areaName}`
  return room.areaName || room.parentAreaName || '-'
}

function liveDurationText(room: BilibiliLiveRoom) {
  if (room.liveStatus !== 1 || !room.liveTime) return '-'
  const diff = Math.max(0, now.value - Date.parse(room.liveTime))
  const hours = Math.floor(diff / 3_600_000)
  const minutes = Math.floor((diff % 3_600_000) / 60_000)
  if (hours > 0) return `${hours} 小时 ${minutes} 分`
  return `${minutes} 分钟`
}

function missingFieldItems(room: BilibiliLiveRoom) {
  return [
    { label: '头像', ok: Boolean(room.faceUrl) },
    { label: '封面', ok: Boolean(room.coverUrl || room.keyframeUrl) },
    { label: '分区', ok: Boolean(room.areaName || room.parentAreaName) },
    { label: '在线', ok: room.onlineCount != null },
    { label: '关注', ok: room.attentionCount != null },
    { label: '标题', ok: Boolean(room.title) },
    { label: '开播时间', ok: room.liveStatus !== 1 || Boolean(room.liveTime) }
  ]
}

function rankSummary(room?: BilibiliLiveRoom) {
  return room ? rankSummariesByRoomId.value[room.id] : undefined
}

function rankSnapshots(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  const latestByKey = new Map<string, BilibiliLiveRankSnapshot>()
  ;(rankSummary(room)?.snapshots ?? [])
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

function rankFamilyTabs(room?: BilibiliLiveRoom) {
  const summary = rankSummary(room)
  return [
    {
      value: 'AUDIENCE' as LiveRankFamily,
      label: '房间观众',
      count: rankCountText(summary?.audienceCount, summary?.audienceCountText)
    },
    {
      value: 'GUARD' as LiveRankFamily,
      label: '大航海',
      count: rankCountText(summary?.guardCount, summary?.guardCountText)
    }
  ]
}

function rankTypeTabsForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  return rankSnapshots(room, family).map((snapshot) => ({
    key: rankSnapshotKey(snapshot),
    label: rankTabLabel(snapshot)
  }))
}

function rankCountText(value?: number, fallback?: string) {
  if (fallback) return fallback
  if (value == null) return '-'
  return new Intl.NumberFormat('zh-CN').format(value)
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

function rankFamilyDescription(family: LiveRankFamily) {
  return family === 'AUDIENCE' ? '贡献值、进房和周期榜单' : '舰长、提督、总督排行'
}

function selectRankTypeForFamily(family: LiveRankFamily, key: string) {
  selectedRankKeyByFamily[family] = key
}

function activeRankKeyForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  const tabs = rankTypeTabsForFamily(room, family)
  const selectedKey = selectedRankKeyByFamily[family]
  if (tabs.some((tab) => tab.key === selectedKey)) return selectedKey
  return tabs[0]?.key || selectedKey
}

function activeRankSnapshotForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  const key = activeRankKeyForFamily(room, family)
  return rankSnapshots(room, family).find((snapshot) => rankSnapshotKey(snapshot) === key)
}

function activeRankEntriesForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  const snapshot = activeRankSnapshotForFamily(room, family)
  return snapshot ? sortRankEntries(snapshot, snapshot.entries).slice(0, MAX_RANK_ENTRIES) : []
}

function activeRankTitleForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  const snapshot = activeRankSnapshotForFamily(room, family)
  return snapshot ? rankSnapshotTitle(snapshot) : '暂无榜单'
}

function activeRankHintForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  const snapshot = activeRankSnapshotForFamily(room, family)
  if (!snapshot) return '刷新后展示最新榜单快照'
  if (snapshot.rankFamily === 'GUARD') {
    if (snapshot.rankType === 'guard_accompany') return '舰长、提督、总督的陪伴天数排行'
    return '舰长、提督、总督周期榜单'
  }
  if (snapshot.rankSwitch === 'entry_time_rank') return '按进房时间记录活跃观众'
  if (snapshot.rankType === 'online_rank') return '投喂、发弹幕均可获得贡献值'
  return '按当前周期统计的贡献值榜单'
}

function activeRankValueLabelForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  const snapshot = activeRankSnapshotForFamily(room, family)
  if (!snapshot) return '数值'
  if (snapshot.rankFamily === 'GUARD') return snapshot.rankType === 'guard_accompany' ? '天数' : '等级'
  if (snapshot.rankSwitch === 'entry_time_rank') return '状态'
  return '贡献值'
}

function rankSortOptionsForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily): RankSortOption[] {
  return rankSnapshots(room, family).map((snapshot) => ({
    key: rankSnapshotKey(snapshot),
    label: rankSortLabel(snapshot),
    description: rankSortDescription(snapshot)
  }))
}

function activeRankSortLabelForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  const snapshot = activeRankSnapshotForFamily(room, family)
  return snapshot ? rankSortLabel(snapshot) : '排序方式'
}

function activeRankSortDirectionForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  return rankSortDirection(activeRankKeyForFamily(room, family))
}

function selectRankSortForFamily(family: LiveRankFamily, command: string | number | object) {
  selectedRankKeyByFamily[family] = String(command)
}

function toggleRankSortDirectionForFamily(room: BilibiliLiveRoom | undefined, family: LiveRankFamily) {
  toggleRankSortDirection(activeRankKeyForFamily(room, family))
}

function activeRankEntryValueForFamily(
  room: BilibiliLiveRoom | undefined,
  family: LiveRankFamily,
  entry: BilibiliLiveRankEntry
) {
  const snapshot = activeRankSnapshotForFamily(room, family)
  if (!snapshot) return '-'
  if (snapshot.rankFamily === 'GUARD' && entry.entryKind === 'EXTOP') return '上期TOP'
  return rankEntryValue(snapshot, entry)
}

function activeRankEntrySubtitleForFamily(
  room: BilibiliLiveRoom | undefined,
  family: LiveRankFamily,
  entry: BilibiliLiveRankEntry
) {
  const snapshot = activeRankSnapshotForFamily(room, family)
  if (snapshot?.rankFamily === 'GUARD') {
    return `${guardLevelText(entry.guardLevel)}${entry.accompanyDays ? ` · 陪伴 ${entry.accompanyDays} 天` : ''}`
  }
  return entry.medalName ? `${entry.medalName}${entry.medalLevel ? ` Lv.${entry.medalLevel}` : ''}` : '无粉丝牌'
}

function activeRankEntryKeyForFamily(
  room: BilibiliLiveRoom | undefined,
  family: LiveRankFamily,
  entry: BilibiliLiveRankEntry,
  index: number
) {
  const snapshot = activeRankSnapshotForFamily(room, family)
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

function rankNoText(rankNo?: number) {
  if (!rankNo) return '-'
  return rankNo <= 3 ? `榜${rankNo}` : `${rankNo}`
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

function rankEntryValue(snapshot: BilibiliLiveRankSnapshot, entry: BilibiliLiveRankEntry) {
  if (snapshot.rankFamily === 'GUARD') {
    if (entry.accompanyDays != null) return `${entry.accompanyDays} 天`
    return guardLevelText(entry.guardLevel)
  }
  if (snapshot.rankSwitch === 'entry_time_rank') return '在房'
  if (entry.score != null) return new Intl.NumberFormat('zh-CN').format(entry.score)
  return guardLevelText(entry.guardLevel)
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

function guardLevelText(level?: number) {
  if (level === 1) return '总督'
  if (level === 2) return '提督'
  if (level === 3) return '舰长'
  return '未上舰'
}

function eventText(event: BilibiliLiveStatusEvent) {
  const room = rooms.value.find((item) => item.id === event.monitorId)
  const name = room?.uname || `房间 ${event.roomId}`
  const eventName: Record<string, string> = {
    LIVE_STARTED: '开播',
    LIVE_ENDED: '下播',
    ROUND_STARTED: '开始轮播',
    TITLE_CHANGED: '标题变化',
    ERROR_OCCURRED: '采集异常',
    ERROR_RECOVERED: '异常恢复'
  }
  return `${name} · ${eventName[event.eventType] || event.eventType}`
}

function formatShortCount(value?: number) {
  if (value == null) return '-'
  if (Math.abs(value) >= 100000000) return `${trimFixed(value / 100000000, 1)}亿`
  if (Math.abs(value) >= 10000) return `${trimFixed(value / 10000, 1)}万`
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatDelta(value?: number) {
  if (value == null) return '-'
  if (value === 0) return '0'
  return `${value > 0 ? '+' : ''}${Math.abs(value) >= 10000 ? formatShortCount(value) : new Intl.NumberFormat('zh-CN').format(value)}`
}

function deltaClass(value?: number) {
  return {
    up: (value ?? 0) > 0,
    down: (value ?? 0) < 0,
    flat: !value
  }
}

function formatInterval(value?: number) {
  if (!value) return '-'
  if (value < 60) return `${value} 秒`
  if (value < 3600) return `${Math.floor(value / 60)} 分${value % 60 ? `${value % 60} 秒` : ''}`
  if (value < 86400) return `${Math.floor(value / 3600)} 小时`
  return `${Math.floor(value / 86400)} 天`
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

function formatTime(value?: string) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date(value))
}

function formatChartTime(value: string) {
  return formatDateTime(value)
}

function relativeTime(value?: string) {
  if (!value) return '-'
  const diff = now.value - Date.parse(value)
  if (!Number.isFinite(diff)) return '-'
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return formatDateTime(value)
}

function trimFixed(value: number, digits: number) {
  return value.toFixed(digits).replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1')
}

function avatarFallback(room: BilibiliLiveRoom) {
  return room.uname?.slice(0, 1).toUpperCase() || String(room.uid).slice(-2)
}

function focusAddInput() {
  document.querySelector<HTMLInputElement>('.live-id-input input')?.focus()
}

function errorMessage(error: unknown) {
  const maybe = error as { response?: { data?: { message?: string } }; message?: string }
  return maybe.response?.data?.message || maybe.message || '请求失败'
}

function applyDocumentTheme(value: MonitorTheme) {
  document.documentElement.dataset.bilibiliLiveTheme = value
}

function loadStoredTheme() {
  const stored = localStorage.getItem(THEME_STORAGE_KEY)
  theme.value = stored === 'dark' ? 'dark' : 'light'
  applyDocumentTheme(theme.value)
}

onMounted(() => {
  loadStoredTheme()
  clockTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  loadAll()
})

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
  if (trendRefreshTimer) window.clearTimeout(trendRefreshTimer)
  delete document.documentElement.dataset.bilibiliLiveTheme
})
</script>

<style>
html[data-bilibili-live-theme='dark'] .shell {
  background: #202938;
}

html[data-bilibili-live-theme='dark'] .sidebar,
html[data-bilibili-live-theme='dark'] .header {
  border-color: rgba(118, 138, 166, 0.2);
  background: #202b3b;
  color: #f3f6fb;
}

html[data-bilibili-live-theme='dark'] .brand,
html[data-bilibili-live-theme='dark'] .header {
  border-color: rgba(118, 138, 166, 0.2);
}

html[data-bilibili-live-theme='dark'] .brand span,
html[data-bilibili-live-theme='dark'] .header-subtitle {
  color: #b8c4d6;
}

html[data-bilibili-live-theme='dark'] .menu {
  --el-menu-bg-color: #202b3b;
  --el-menu-text-color: #d7dee9;
  --el-menu-hover-bg-color: #2b384b;
  --el-menu-active-color: #9ccbff;
}

html[data-bilibili-live-theme='dark'] .main {
  background: #202938;
}

.live-page {
  --page-bg: #f6f8fb;
  --surface: #fffefa;
  --surface-strong: #ffffff;
  --border: #e1d8cf;
  --border-soft: #efe7dd;
  --text: #071328;
  --muted: #647084;
  --muted-strong: #344054;
  --accent: #2f6df6;
  --accent-soft: #ecf3ff;
  --positive: #00845f;
  --negative: #c23a2b;
  --warning: #b76e00;
  --shadow: 0 14px 36px rgba(29, 41, 57, 0.08);
  gap: 14px;
  min-height: calc(100vh - 104px);
  color: var(--text);
  line-height: 1.45;
  font-variant-numeric: tabular-nums;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.7), rgba(255, 255, 255, 0)),
    var(--page-bg);
}

.live-page.is-dark {
  --page-bg: #202938;
  --surface: rgba(38, 49, 66, 0.9);
  --surface-strong: rgba(45, 58, 78, 0.88);
  --border: rgba(118, 138, 166, 0.28);
  --border-soft: rgba(103, 123, 151, 0.22);
  --text: #f3f6fb;
  --muted: #b8c4d6;
  --muted-strong: #dbe4f1;
  --accent: #74a8f5;
  --accent-soft: rgba(116, 168, 245, 0.18);
  --positive: #43d0a2;
  --negative: #ff8a8a;
  --warning: #f6c66f;
  --shadow: 0 16px 34px rgba(8, 14, 24, 0.2);
  background:
    radial-gradient(circle at 18% 0%, rgba(235, 122, 171, 0.09), transparent 34%),
    radial-gradient(circle at 84% 6%, rgba(116, 168, 245, 0.1), transparent 31%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.025), rgba(255, 255, 255, 0)),
    var(--page-bg);
}

.live-page.is-dark :where(
  .live-control-surface,
  .room-strip-section,
  .live-trend-section,
  .event-section,
  .summary-item,
  .room-detail,
  .trend-card,
  .room-tile,
  .detail-grid > div,
  .detail-bottom,
  .data-health
) {
  backdrop-filter: blur(10px) saturate(112%);
}

.live-page.is-dark :where(.el-input__wrapper, .el-input-number .el-input__wrapper) {
  background: rgba(45, 58, 78, 0.9);
  box-shadow: 0 0 0 1px rgba(130, 151, 181, 0.28) inset;
  backdrop-filter: blur(8px) saturate(112%);
}

.live-page.is-dark :where(.el-input__wrapper:hover, .el-input-number .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(139, 178, 235, 0.48) inset;
}

.live-page.is-dark :where(.el-input__wrapper.is-focus, .el-input-number .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(139, 178, 235, 0.72) inset, 0 0 0 3px rgba(116, 168, 245, 0.13);
}

.live-page.is-dark .el-input__inner {
  color: var(--text);
  -webkit-text-fill-color: var(--text);
}

.live-page.is-dark .el-input__inner::placeholder {
  color: #a9b6c9;
  -webkit-text-fill-color: #a9b6c9;
}

.live-page.is-dark .el-segmented {
  --el-segmented-bg-color: rgba(45, 58, 78, 0.9);
  --el-segmented-color: var(--muted-strong);
  --el-segmented-item-selected-bg-color: var(--accent);
  --el-segmented-item-selected-color: #ffffff;
  --el-segmented-item-hover-bg-color: rgba(59, 78, 104, 0.92);
  border: 1px solid rgba(130, 151, 181, 0.26);
  backdrop-filter: blur(8px) saturate(112%);
}

.live-page.is-dark .el-button {
  --el-button-bg-color: rgba(45, 58, 78, 0.9);
  --el-button-border-color: rgba(130, 151, 181, 0.34);
  --el-button-text-color: var(--muted-strong);
  --el-button-hover-bg-color: rgba(59, 78, 104, 0.94);
  --el-button-hover-border-color: var(--accent);
  --el-button-hover-text-color: #ffffff;
  --el-button-active-bg-color: rgba(39, 52, 72, 0.98);
  --el-button-active-border-color: var(--accent);
  backdrop-filter: blur(8px) saturate(112%);
}

.live-page.is-dark .el-button--primary {
  --el-button-bg-color: #4f94f7;
  --el-button-border-color: #64a5ff;
  --el-button-text-color: #ffffff;
  --el-button-hover-bg-color: #6ba9ff;
  --el-button-hover-border-color: #8abdff;
  --el-button-active-bg-color: #3f82e4;
}

.live-page.is-dark .el-button--danger {
  --el-button-bg-color: rgba(255, 139, 139, 0.12);
  --el-button-border-color: rgba(255, 139, 139, 0.54);
  --el-button-text-color: #ffb7b7;
  --el-button-hover-bg-color: rgba(255, 139, 139, 0.22);
  --el-button-hover-border-color: #ff9e9e;
  --el-button-hover-text-color: #ffffff;
}

.live-page.is-dark :where(.notice-alert, .error-alert, .detail-alert) {
  border-width: 1px;
}

.live-page.is-dark .notice-alert {
  border-color: rgba(255, 202, 115, 0.42);
  background: rgba(255, 202, 115, 0.18);
  backdrop-filter: blur(12px) saturate(120%);
}

.live-page.is-dark .notice-alert :where(.el-alert__title, .el-alert__content, .el-alert__icon) {
  color: #ffd890;
}

.live-page.is-dark :where(.error-alert, .detail-alert) {
  border-color: rgba(255, 139, 139, 0.42);
  background: rgba(255, 139, 139, 0.18);
  backdrop-filter: blur(12px) saturate(120%);
}

.live-page.is-dark :where(.error-alert, .detail-alert) :where(.el-alert__title, .el-alert__content, .el-alert__icon) {
  color: #ffc2c2;
}

.live-page.is-dark .el-switch {
  --el-switch-off-color: #4a5b74;
  --el-switch-on-color: var(--accent);
}

.live-header {
  position: relative;
  align-items: flex-start;
  isolation: isolate;
  overflow: hidden;
  padding: 20px 24px 22px;
  border: 0;
  border-radius: 0;
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--surface-strong) 46%, transparent), color-mix(in srgb, var(--surface) 28%, transparent) 58%, transparent),
    radial-gradient(ellipse at 10% 6%, rgba(235, 122, 171, 0.13), transparent 44%),
    radial-gradient(ellipse at 88% 14%, rgba(116, 168, 245, 0.1), transparent 46%);
  box-shadow: none;
}

.live-header::before {
  display: none;
}

.live-header::after {
  display: none;
}

.live-header > div {
  position: relative;
  z-index: 1;
}

.live-page.is-dark .live-header {
  background:
    radial-gradient(ellipse at 10% 6%, rgba(235, 122, 171, 0.18), transparent 45%),
    radial-gradient(ellipse at 88% 14%, rgba(116, 168, 245, 0.13), transparent 48%),
    linear-gradient(90deg, rgba(55, 43, 62, 0.42), rgba(37, 49, 66, 0.42) 52%, rgba(35, 45, 61, 0.18) 82%, transparent);
}

.live-header .page-title {
  margin: 0;
  color: var(--text);
  font-size: 24px;
  line-height: 1.25;
  letter-spacing: 0;
}

.live-header .page-subtitle {
  margin-top: 7px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.65;
}

.live-control-surface,
.room-strip-section,
.live-trend-section,
.event-section,
.summary-item,
.room-detail,
.trend-card {
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent);
  border-radius: 12px;
  background: var(--surface);
  box-shadow:
    var(--shadow),
    inset 0 1px 0 color-mix(in srgb, #ffffff 4%, transparent),
    inset 0 -1px 0 color-mix(in srgb, #000000 8%, transparent);
}

.live-control-surface {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 20px 22px;
}

.add-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.live-control-surface .el-form-item__label {
  color: var(--muted);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.live-control-surface .el-button,
.detail-actions .el-button {
  font-weight: 700;
}

.live-id-input {
  width: 190px;
}

.interval-input {
  width: 128px;
}

.unit {
  margin-left: 6px;
  color: var(--muted);
  font-size: 13px;
}

.control-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 7px 11px;
  border: 1px solid color-mix(in srgb, var(--border) 72%, transparent);
  border-radius: 10px;
  color: var(--muted-strong);
  background: linear-gradient(180deg, color-mix(in srgb, var(--surface-strong) 94%, transparent), var(--surface));
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
}

.theme-label {
  color: var(--text);
  font-weight: 800;
}

.toolbar-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  color: var(--muted-strong);
  font-size: 13px;
  line-height: 1.25;
}

.toolbar-meta span,
.section-heading > span,
.refresh-pill {
  padding: 6px 11px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 70%, transparent);
  border-radius: 999px;
  background: var(--accent-soft);
  font-weight: 700;
  white-space: nowrap;
}

.notice-alert,
.error-alert {
  border-radius: 12px;
}

.notice-alert .el-alert__title,
.error-alert .el-alert__title,
.detail-alert .el-alert__title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.58;
}

.live-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-item {
  min-width: 0;
  padding: 18px 22px;
}

.summary-item span {
  display: block;
  color: var(--muted);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
}

.summary-item strong {
  display: block;
  margin-top: 8px;
  color: var(--text);
  font-size: 25px;
  font-weight: 900;
  line-height: 1.16;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-strip-section,
.live-trend-section,
.event-section {
  padding: 20px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--muted);
}

.section-heading h2 {
  margin: 0;
  color: var(--text);
  font-size: 21px;
  line-height: 1.3;
}

.section-heading p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.65;
  max-width: 680px;
}

.room-strip {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(220px, 1fr);
  gap: 12px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 2px 2px 12px;
  scrollbar-color: color-mix(in srgb, var(--accent) 42%, var(--border)) transparent;
  scrollbar-width: thin;
}

.room-tile {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 220px;
  max-width: 310px;
  min-height: 204px;
  padding: 16px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 82%, transparent);
  border-radius: 12px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--room-accent) 10%, transparent), transparent 58%),
    var(--surface-strong);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease, opacity 0.18s ease;
}

.room-tile:hover,
.room-tile.expanded,
.room-tile.selected {
  border-color: color-mix(in srgb, var(--room-accent) 48%, var(--border));
  box-shadow: 0 12px 26px color-mix(in srgb, var(--room-accent) 15%, transparent);
}

.room-tile.paused {
  opacity: 0.62;
}

.tile-head {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.avatar-shell {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  overflow: hidden;
  border-radius: 10px;
  color: #fff;
  background: linear-gradient(135deg, var(--room-accent), #16233e);
  font-weight: 900;
}

.avatar-shell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.tile-copy {
  min-width: 0;
}

.tile-copy strong,
.trend-card-title strong,
.detail-title-row h3 {
  display: block;
  overflow: hidden;
  color: var(--text);
  font-weight: 900;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tile-copy strong {
  font-size: 16px;
}

.tile-copy span,
.trend-card-title span {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  height: 26px;
  padding: 0 8px;
  border-radius: 999px;
  color: var(--muted-strong);
  background: color-mix(in srgb, var(--border-soft) 75%, transparent);
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.status-pill.live {
  color: #fff;
  background: #12a878;
}

.status-pill.round {
  color: #fff;
  background: #2f80ed;
}

.status-pill.error {
  color: #fff;
  background: var(--negative);
}

.status-pill.paused {
  color: var(--muted);
}

.status-pill.large {
  position: absolute;
  top: 12px;
  left: 12px;
}

.room-title {
  display: -webkit-box;
  min-height: 42px;
  margin: 0;
  overflow: hidden;
  color: var(--muted-strong);
  font-size: 13.5px;
  font-weight: 500;
  line-height: 1.62;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.tile-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.tile-metrics span {
  display: block;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
}

.tile-metrics strong {
  display: block;
  margin-top: 5px;
  color: var(--text);
  font-size: 21px;
  font-weight: 900;
  line-height: 1.15;
  overflow: hidden;
  text-overflow: ellipsis;
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

.tile-rhythm {
  display: grid;
  gap: 6px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.35;
}

.rhythm-bar {
  height: 5px;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--border-soft) 72%, transparent);
}

.rhythm-bar i {
  display: block;
  width: var(--collect-progress);
  min-width: 8px;
  max-width: 100%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--room-accent), #f5b66d);
  transition: width 0.4s ease;
}

.tile-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.icon-action,
.trend-action,
.detail-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid color-mix(in srgb, var(--border) 78%, transparent);
  border-radius: 10px;
  color: var(--muted-strong);
  background: var(--surface);
  cursor: pointer;
  text-decoration: none;
}

.icon-action {
  width: 30px;
  height: 30px;
}

.trend-action {
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 700;
}

.trend-action.active {
  color: #fff;
  border-color: var(--room-accent);
  background: var(--room-accent);
}

.trend-action:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.room-detail {
  display: block;
  margin-top: 14px;
  padding: 20px;
  background:
    radial-gradient(circle at 18% 12%, color-mix(in srgb, var(--room-accent) 12%, transparent), transparent 30%),
    linear-gradient(135deg, color-mix(in srgb, var(--room-accent) 5%, transparent), transparent 48%),
    var(--surface-strong);
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

.detail-preview {
  position: relative;
  align-self: start;
  width: fit-content;
  max-width: clamp(260px, 25vw, 380px);
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: visible;
  border-radius: 14px;
  background: transparent;
}

.detail-preview img {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 150px;
  border-radius: 14px;
  box-shadow:
    0 14px 28px color-mix(in srgb, #0f172a 12%, transparent),
    0 0 0 1px color-mix(in srgb, var(--border-soft) 72%, transparent);
  object-fit: contain;
}

.preview-fallback {
  display: grid;
  place-items: center;
  width: 260px;
  min-height: 132px;
  border: 1px dashed color-mix(in srgb, var(--border-soft) 85%, transparent);
  border-radius: 14px;
  color: var(--muted);
  background: color-mix(in srgb, var(--page-bg) 60%, transparent);
}

.preview-fallback .el-icon {
  font-size: 38px;
}

.detail-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.detail-title-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 68%, transparent);
  border-radius: 18px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--room-accent) 8%, transparent), transparent 52%),
    color-mix(in srgb, var(--surface) 86%, transparent);
}

.detail-heading-group {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.detail-title-copy {
  min-width: 0;
}

.detail-title-row h3 {
  margin: 0;
  font-size: 24px;
  line-height: 1.25;
}

.detail-title-row p {
  margin: 6px 0 0;
  color: var(--muted-strong);
  font-size: 14px;
  line-height: 1.55;
}

.detail-title-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.detail-title-meta span {
  max-width: 220px;
  overflow: hidden;
  padding: 5px 10px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 72%, transparent);
  border-radius: 999px;
  color: var(--muted-strong);
  background: color-mix(in srgb, var(--surface) 72%, transparent);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
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

.detail-link {
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 800;
}

.detail-alert {
  border-radius: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.detail-grid > div {
  min-width: 0;
  padding: 11px 13px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 58%, transparent);
  border-radius: 12px;
  background:
    linear-gradient(180deg, color-mix(in srgb, #fff 38%, transparent), transparent),
    color-mix(in srgb, var(--surface) 76%, transparent);
}

.detail-grid span,
.interval-editor > span,
.data-health > span {
  display: block;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
}

.detail-grid strong {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: var(--text);
  font-size: 15.5px;
  font-weight: 800;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-bottom {
  display: grid;
  grid-template-columns: minmax(340px, 520px) minmax(0, 1fr);
  gap: 12px;
}

.interval-editor {
  display: grid;
  grid-template-columns: auto minmax(90px, 1fr) auto auto;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 11px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 78%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--surface) 90%, transparent);
}

.interval-editor.dirty {
  border-color: color-mix(in srgb, var(--warning) 55%, var(--border));
  background: color-mix(in srgb, var(--warning) 10%, var(--surface));
}

.data-health {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 78%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--surface) 88%, transparent);
}

.data-health div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.data-health em {
  padding: 4px 8px;
  border-radius: 999px;
  color: var(--positive);
  background: color-mix(in srgb, var(--positive) 10%, transparent);
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
  line-height: 1.25;
}

.data-health em.missing {
  color: var(--warning);
  background: color-mix(in srgb, var(--warning) 12%, transparent);
}

.rank-panel {
  min-width: 0;
  padding: 16px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 66%, transparent);
  border-radius: 18px;
  background:
    radial-gradient(circle at 10% 0%, color-mix(in srgb, var(--accent) 7%, transparent), transparent 30%),
    color-mix(in srgb, var(--surface) 83%, transparent);
}

.rank-panel-head,
.rank-actions,
.rank-snapshot-title,
.rank-entry {
  display: flex;
  align-items: center;
}

.rank-panel-head {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.rank-panel h4,
.rank-panel h5 {
  margin: 0;
  color: var(--text);
}

.rank-panel h4 {
  font-size: 17px;
  line-height: 1.25;
}

.rank-panel h5 {
  margin-bottom: 8px;
  font-size: 14px;
}

.rank-panel p,
.rank-actions span,
.rank-snapshot-title span,
.rank-entry em {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.35;
}

.rank-actions {
  flex: 0 0 auto;
  gap: 10px;
}

.rank-board {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.rank-board-column {
  min-width: 0;
  padding: 14px 14px 16px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 52%, transparent);
  border-radius: 16px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--primary) 5%, transparent), transparent 44%),
    color-mix(in srgb, var(--page-bg) 52%, var(--surface));
}

.rank-column-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid color-mix(in srgb, var(--border-soft) 48%, transparent);
}

.rank-column-head > div {
  min-width: 0;
}

.rank-column-head h5 {
  margin: 0;
  font-size: 16px;
  line-height: 1.25;
}

.rank-column-head p {
  margin-top: 5px;
}

.rank-column-head > span {
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  color: color-mix(in srgb, var(--primary) 82%, var(--text));
  background: color-mix(in srgb, var(--primary) 10%, transparent);
  font-size: 12px;
  font-weight: 800;
  line-height: 1.2;
}

.rank-family-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  margin: 0 0 10px;
  border-bottom: 1px solid color-mix(in srgb, var(--border-soft) 76%, transparent);
}

.rank-family-tab,
.rank-type-tab {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.rank-family-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  padding: 8px 10px 10px;
  border-bottom: 2px solid transparent;
  color: var(--muted);
  background: transparent;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.rank-family-tab strong {
  overflow: hidden;
  font-size: 14px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-family-tab span {
  color: color-mix(in srgb, var(--muted) 86%, transparent);
  font-size: 12px;
}

.rank-family-tab.active {
  color: color-mix(in srgb, var(--accent) 82%, var(--text));
  border-color: color-mix(in srgb, var(--accent) 84%, var(--primary));
  background: linear-gradient(180deg, color-mix(in srgb, var(--accent) 9%, transparent), transparent);
}

.rank-family-tab.active span {
  color: color-mix(in srgb, var(--accent) 76%, var(--muted));
}

.rank-type-tabs {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 2px -2px 12px;
  padding: 5px 2px 4px;
  overflow-x: auto;
}

.rank-type-tab {
  flex: 0 0 auto;
  min-width: 58px;
  padding: 5px 11px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 78%, transparent);
  border-radius: 999px;
  color: var(--muted);
  background: color-mix(in srgb, var(--surface) 84%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 50%, transparent);
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease,
    transform 0.18s ease;
}

.rank-type-tab:hover {
  color: var(--text);
  border-color: color-mix(in srgb, var(--accent) 40%, var(--border-soft));
}

.rank-type-tab.active {
  color: color-mix(in srgb, var(--accent) 82%, var(--text));
  border-color: color-mix(in srgb, var(--accent) 42%, var(--border-soft));
  background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 16%, var(--surface)), var(--surface));
  transform: none;
}

.rank-view {
  min-width: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.rank-view-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding: 8px 2px 0;
}

.rank-view-head > div {
  min-width: 0;
}

.rank-view-head strong {
  display: block;
  overflow: hidden;
  color: var(--text);
  font-size: 14px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-view-head span {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.35;
}

.rank-view-head > span {
  flex: 0 0 auto;
  padding-top: 1px;
}

.rank-sort-controls {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  padding-top: 1px;
}

.rank-sort-button,
.rank-direction-button {
  border: 1px solid color-mix(in srgb, var(--border-soft) 78%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 88%, transparent);
  cursor: pointer;
  font: inherit;
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 46%, transparent);
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
  max-width: 146px;
  padding: 6px 10px;
  color: color-mix(in srgb, var(--accent) 82%, var(--primary));
  font-size: 12px;
  font-weight: 900;
}

.rank-sort-button span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-sort-button em {
  color: color-mix(in srgb, var(--accent) 68%, var(--muted));
  font-style: normal;
}

.rank-direction-button {
  padding: 6px 9px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
}

.rank-sort-button:hover,
.rank-direction-button:hover {
  border-color: color-mix(in srgb, var(--accent) 42%, var(--border-soft));
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 12%, transparent), transparent),
    color-mix(in srgb, var(--surface) 92%, transparent);
  box-shadow: 0 8px 18px color-mix(in srgb, var(--accent) 10%, transparent);
}

.rank-sort-option {
  display: grid;
  gap: 2px;
  min-width: 146px;
}

.rank-sort-option strong {
  color: var(--text);
  font-size: 13px;
  font-weight: 900;
  line-height: 1.25;
}

.rank-sort-option span {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.35;
}

.rank-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.rank-metrics > div {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--page-bg) 70%, var(--surface));
}

.rank-metrics span,
.rank-metrics em {
  display: block;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-metrics strong {
  display: block;
  margin: 4px 0 2px;
  overflow: hidden;
  color: var(--text);
  font-size: 22px;
  font-weight: 900;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.rank-snapshot {
  min-width: 0;
  padding: 10px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 70%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
}

.rank-snapshot + .rank-snapshot {
  margin-top: 8px;
}

.rank-snapshot-title {
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.rank-snapshot-title strong {
  overflow: hidden;
  color: var(--text);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-snapshot-title span {
  flex: 0 0 auto;
  margin: 0;
}

.rank-entry-list {
  display: grid;
  gap: 6px;
  max-height: min(760px, 58vh);
  padding-right: 4px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.rank-entry-list-large {
  gap: 9px;
}

.rank-entry-list::-webkit-scrollbar {
  width: 9px;
}

.rank-entry-list::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: color-mix(in srgb, var(--primary) 46%, transparent);
  background-clip: content-box;
}

.rank-entry-list::-webkit-scrollbar-track {
  border-radius: 999px;
  background: color-mix(in srgb, var(--page-bg) 80%, transparent);
}

.rank-entry {
  min-width: 0;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--page-bg) 72%, var(--surface));
}

.rank-entry-large {
  min-height: 54px;
  padding: 10px 11px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 34%, transparent);
  border-radius: 14px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 4%, transparent), transparent 58%),
    color-mix(in srgb, var(--surface) 88%, transparent);
  box-shadow: 0 8px 18px color-mix(in srgb, #0f172a 3.5%, transparent);
}

.rank-no {
  flex: 0 0 34px;
  color: var(--primary);
  font-size: 12px;
  font-weight: 900;
}

.rank-entry-large .rank-no {
  flex-basis: 38px;
  color: var(--text);
}

.rank-entry-large .rank-no.podium {
  flex-basis: 42px;
  padding: 4px 7px;
  border-radius: 999px;
  color: #fff;
  text-align: center;
  background: linear-gradient(135deg, #ff8f98, #ec5fa8);
  box-shadow: 0 8px 18px color-mix(in srgb, #ec5fa8 14%, transparent);
}

.rank-entry img,
.rank-avatar {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border-radius: 8px;
}

.rank-entry-large img,
.rank-entry-large .rank-avatar {
  flex-basis: 36px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.rank-entry img {
  object-fit: cover;
}

.rank-avatar {
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  font-size: 12px;
  font-weight: 900;
}

.rank-entry > div {
  min-width: 0;
  flex: 1 1 auto;
}

.rank-user {
  display: grid;
  gap: 2px;
}

.rank-entry strong,
.rank-entry em {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-entry strong {
  color: var(--text);
  font-size: 13px;
  line-height: 1.25;
}

.rank-entry-large strong {
  font-size: 14px;
}

.rank-entry b {
  flex: 0 0 auto;
  max-width: 90px;
  overflow: hidden;
  color: var(--positive);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-entry-large b {
  min-width: 62px;
  max-width: 110px;
  font-size: 15px;
  text-align: right;
}

.trend-empty {
  min-height: 260px;
}

.trend-grid {
  display: grid;
  gap: 12px;
}

.trend-grid.count-1 {
  grid-template-columns: minmax(0, 1fr);
}

.trend-grid.count-2,
.trend-grid.count-4 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.trend-grid.count-3 {
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.9fr);
  grid-template-rows: repeat(2, minmax(0, auto));
}

.trend-grid.count-3 .trend-card:first-child {
  grid-row: 1 / span 2;
}

.trend-card {
  min-width: 0;
  padding: 20px;
}

.trend-card-title {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  margin-bottom: 10px;
}

.trend-card-title strong {
  font-size: 16px;
}

.trend-card-title > div {
  min-width: 0;
}

.trend-card-title > div:last-child {
  flex: 0 0 auto;
  text-align: right;
}

.chart-note {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.45;
}

.refresh-pill {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent) 44%, var(--border));
  background: color-mix(in srgb, var(--accent) 10%, var(--surface));
  font-size: 12px;
}

.event-list {
  display: grid;
  gap: 8px;
}

.event-list article {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--border-soft) 78%, transparent);
  border-radius: 10px;
  color: var(--muted-strong);
  background: color-mix(in srgb, var(--surface-strong) 86%, transparent);
  line-height: 1.45;
}

.event-list strong {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 1280px) {
  .live-control-surface,
  .room-detail {
    grid-template-columns: 1fr;
  }

  .detail-title-row {
    grid-template-columns: 1fr;
  }

  .detail-actions {
    justify-content: flex-start;
  }

  .live-control-surface {
    align-items: stretch;
    flex-direction: column;
  }

  .control-actions {
    justify-content: flex-start;
  }

  .detail-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .detail-bottom {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .live-summary-grid,
  .trend-grid.count-2,
  .trend-grid.count-3,
  .trend-grid.count-4 {
    grid-template-columns: 1fr;
  }

  .trend-grid.count-3 .trend-card:first-child {
    grid-row: auto;
  }

  .room-detail,
  .detail-title-row {
    grid-template-columns: 1fr;
  }

  .rank-board {
    grid-template-columns: 1fr;
  }

  .detail-heading-group {
    flex-direction: column;
    align-items: stretch;
  }

  .detail-preview {
    flex-basis: auto;
    max-width: 100%;
  }

  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .live-summary-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .interval-editor {
    grid-template-columns: 1fr;
  }
}
</style>
