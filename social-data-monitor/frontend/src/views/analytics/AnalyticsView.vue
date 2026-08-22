<template>
  <section class="page analytics-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">分析看板</h1>
        <p class="page-subtitle">围绕单场直播判断互动效率、付费转化、用户结构和可行动信号。</p>
      </div>
      <el-button :icon="Refresh" type="primary" :loading="loading" @click="polling.refreshNow">立即刷新</el-button>
    </header>

    <el-card class="control-card" shadow="never">
      <div class="control-grid">
        <label>
          <span>直播间</span>
          <el-select v-model="selectedMonitorId" placeholder="选择直播间" filterable>
            <el-option
              v-for="room in rooms"
              :key="room.id"
              :label="`${room.uname || '未知主播'} · ${room.roomId}`"
              :value="room.id"
            />
          </el-select>
        </label>
        <label class="session-control">
          <span>分析场次</span>
          <el-select v-model="selectedSessionId" placeholder="选择场次" filterable>
            <el-option
              v-for="session in sessions"
              :key="session.id"
              :label="`#${session.id} · ${formatDateTime(session.startedAt)} · ${sessionStateLabel(session.state)}`"
              :value="session.id"
            />
          </el-select>
        </label>
        <label>
          <span>时间粒度</span>
          <el-select v-model="bucketSeconds">
            <el-option label="1 分钟" :value="60" />
            <el-option label="5 分钟" :value="300" />
            <el-option label="15 分钟" :value="900" />
          </el-select>
        </label>
        <label>
          <span>自动刷新</span>
          <div class="refresh-control">
            <el-switch v-model="autoRefresh" />
            <el-input-number v-model="refreshSeconds" :min="3" :max="3600" controls-position="right" />
            <em>秒</em>
          </div>
        </label>
      </div>
    </el-card>

    <template v-if="insight && selectedSession">
      <el-alert class="coverage-alert" :title="insight.quality.caveat" type="warning" :closable="false" show-icon />

      <section class="kpi-grid">
        <article class="kpi-card kpi-card--accent">
          <span>弹幕速率</span>
          <strong>{{ formatInsightRate(insight.kpis.danmakuPerMinute) }}</strong>
          <small>按 {{ formatSecondsDuration(insight.quality.coveredSeconds) }} 在线覆盖折算</small>
        </article>
        <article class="kpi-card">
          <span>付费转化率</span>
          <strong>{{ formatInsightPercent(insight.kpis.payerConversionRate) }}</strong>
          <small>付费 UID / 互动 UID</small>
        </article>
        <article class="kpi-card">
          <span>付费金额</span>
          <strong class="money">{{ formatMilliYuan(insight.kpis.paidAmountMilliYuan) }}</strong>
          <small>{{ formatInteger(selectedSession.paidEventCount) }} 个付费事件</small>
        </article>
        <article class="kpi-card">
          <span>ARPPU</span>
          <strong>{{ formatMilliYuan(insight.kpis.arppuMilliYuan) }}</strong>
          <small>每位已识别付费用户</small>
        </article>
        <article class="kpi-card">
          <span>Top 5 收入占比</span>
          <strong>{{ formatInsightPercent(insight.kpis.topFiveRevenueShare) }}</strong>
          <small>衡量付费集中度</small>
        </article>
      </section>

      <el-card class="signal-card" shadow="never">
        <div class="card-heading">
          <div>
            <h2>互动与付费信号轨</h2>
            <p>所有峰值使用同一场次时间轴；只表示同时段信号，不推断因果。</p>
          </div>
          <span>{{ bucketSeconds / 60 }} 分钟粒度</span>
        </div>
        <div class="signal-rail" :class="{ 'signal-rail--empty': signalMarkers.length === 0 }">
          <div class="rail-line"></div>
          <span class="rail-start">{{ timelineStart }}</span>
          <span class="rail-end">{{ timelineEnd }}</span>
          <button
            v-for="marker in signalMarkers"
            :key="`${marker.type}-${marker.bucketStart}`"
            class="signal-marker"
            :class="`signal-marker--${marker.type.toLowerCase()}`"
            :style="{ left: `${marker.position}%` }"
            :title="`${marker.label} · ${formatDateTime(marker.bucketStart)}`"
          >
            <i></i><b>{{ marker.label }}</b>
          </button>
          <p v-if="signalMarkers.length === 0">当前场次还没有可标记的互动或付费峰值</p>
        </div>
      </el-card>

      <section class="chart-grid">
        <el-card shadow="never">
          <div class="card-heading compact">
            <div><h2>互动趋势</h2><p>弹幕量与已识别活跃用户按同一时间桶聚合。</p></div>
          </div>
          <TrendChart
            :labels="timelineLabels"
            :timestamps="timelineTimestamps"
            :series="interactionSeries"
            :height="260"
          />
        </el-card>
        <el-card shadow="never">
          <div class="card-heading compact">
            <div><h2>付费趋势</h2><p>金额按元展示，保留后端毫元精度计算结果。</p></div>
          </div>
          <TrendChart
            :labels="timelineLabels"
            :timestamps="timelineTimestamps"
            :series="revenueSeries"
            :height="260"
            accent-color="#16a071"
          />
        </el-card>
      </section>

      <section class="depth-grid">
        <el-card class="depth-card" shadow="never">
          <div class="card-heading compact">
            <div>
              <h2>弹幕参与深度</h2>
              <p>只按可识别正 UID 计算参与率；直播弹幕时段等分为开场、中段、收尾。</p>
            </div>
            <span>{{ formatInteger(insight.danmakuDepth.identifiedDanmakuUserCount) }} 人</span>
          </div>
          <div class="depth-metrics depth-metrics--four">
            <article>
              <span>已识别人均弹幕</span>
              <strong>{{ formatDecimal(insight.danmakuDepth.messagesPerActiveUser) }}</strong>
              <small>已识别弹幕 / 弹幕 UID</small>
            </article>
            <article>
              <span>重复互动率</span>
              <strong>{{ formatInsightPercent(insight.danmakuDepth.repeatInteractionRate) }}</strong>
              <small>至少发送 3 条弹幕</small>
            </article>
            <article>
              <span>持续参与率</span>
              <strong>{{ formatInsightPercent(insight.danmakuDepth.sustainedParticipationRate) }}</strong>
              <small>至少活跃在 2 个阶段</small>
            </article>
            <article>
              <span>重复内容占比</span>
              <strong>{{ formatInsightPercent(insight.danmakuDepth.duplicateMessageRate) }}</strong>
              <small>文本重复，不直接判定刷屏</small>
            </article>
          </div>
          <div class="depth-section">
            <div class="section-label"><strong>阶段参与</strong><span>消息占比 · 活跃 UID</span></div>
            <div class="stage-list">
              <div v-for="stage in insight.danmakuDepth.stages" :key="stage.code">
                <span>{{ stage.label }}</span>
                <div class="depth-track"><i :style="{ width: percentBarWidth(stage.messageShare) }"></i></div>
                <b>{{ formatInsightPercent(stage.messageShare) }}</b>
                <em>{{ formatInteger(stage.activeUserCount) }} 人</em>
              </div>
            </div>
          </div>
          <div class="depth-section">
            <div class="section-label"><strong>高频弹幕</strong><span>标准化文本 Top 5</span></div>
            <div v-if="insight.danmakuDepth.repeatedMessages.length" class="repeated-list">
              <div v-for="message in insight.danmakuDepth.repeatedMessages" :key="message.messageText">
                <strong :title="message.messageText">{{ message.messageText }}</strong>
                <span>{{ formatInteger(message.messageCount) }} 条 · {{ formatInteger(message.userCount) }} 个 UID</span>
              </div>
            </div>
            <p v-else class="depth-empty">本场没有出现至少 2 次的相同弹幕文本</p>
          </div>
        </el-card>

        <el-card class="depth-card" shadow="never">
          <div class="card-heading compact">
            <div>
              <h2>付费用户深度</h2>
              <p>聚焦可识别付费 UID 的复购、互动后转化与收入结构。</p>
            </div>
            <span>{{ formatInteger(insight.paymentDepth.payerCount) }} 人</span>
          </div>
          <div class="depth-metrics">
            <article>
              <span>场内复购率</span>
              <strong>{{ formatInsightPercent(insight.paymentDepth.repeatPayerRate) }}</strong>
              <small>本场至少 2 次付费事件</small>
            </article>
            <article>
              <span>互动付费者</span>
              <strong>{{ formatInsightPercent(insight.paymentDepth.engagedPayerRate) }}</strong>
              <small>本场也发送过弹幕</small>
            </article>
            <article>
              <span>历史复购者</span>
              <strong>{{ formatInsightPercent(insight.paymentDepth.returningPayerRate) }}</strong>
              <small>在已记录更早场次付费过</small>
            </article>
            <article>
              <span>付费中位数</span>
              <strong>{{ formatMilliYuan(insight.paymentDepth.medianPayerAmountMilliYuan) }}</strong>
              <small>降低极端大额的干扰</small>
            </article>
            <article>
              <span>互动后付费中位时长</span>
              <strong>{{ formatSecondsDuration(insight.paymentDepth.medianConversionLagSeconds) }}</strong>
              <small>仅统计先弹幕后付费者</small>
            </article>
            <article>
              <span>Top 1 收入占比</span>
              <strong>{{ formatInsightPercent(insight.paymentDepth.topOneRevenueShare) }}</strong>
              <small>识别单一大额依赖</small>
            </article>
          </div>
          <div class="depth-section">
            <div class="section-label"><strong>消费层级</strong><span>&lt;1 元 / 1–10 元 / ≥10 元</span></div>
            <div class="tier-list">
              <div v-for="tier in insight.paymentDepth.spendTiers" :key="tier.code">
                <div><strong>{{ tier.label }}</strong><span>{{ formatInteger(tier.userCount) }} 人</span></div>
                <div class="depth-track depth-track--green"><i :style="{ width: percentBarWidth(tier.revenueShare) }"></i></div>
                <b>{{ formatMilliYuan(tier.paidAmountMilliYuan) }}</b>
                <em>{{ formatInsightPercent(tier.revenueShare) }}</em>
              </div>
            </div>
          </div>
          <p class="method-note">历史复购仅基于本项目同一直播间已记录的更早场次，不代表用户在平台上的全部付费历史。</p>
        </el-card>
      </section>

      <section class="detail-grid">
        <el-card class="detail-card" shadow="never">
          <div class="card-heading compact">
            <div><h2>用户分层</h2><p>只统计正 UID，四类互斥。</p></div>
            <span>{{ identifiedSegmentUsers }} 人</span>
          </div>
          <div class="bar-list">
            <div v-for="segment in insight.userSegments" :key="segment.code" class="bar-row">
              <div><strong>{{ segment.label }}</strong><span>{{ segment.description }}</span></div>
              <div class="bar-track"><i :style="{ width: segmentBarWidth(segment.userCount) }"></i></div>
              <b>{{ segment.userCount }}</b>
            </div>
          </div>
        </el-card>

        <el-card class="detail-card" shadow="never">
          <div class="card-heading compact">
            <div><h2>礼物结构</h2><p>按付费金额、数量排序的 Top 12。</p></div>
          </div>
          <div v-if="insight.giftMix.length" class="gift-list">
            <div v-for="gift in insight.giftMix" :key="`${gift.eventKind}-${gift.giftName}`">
              <div><strong>{{ giftDisplayName(gift.eventKind, gift.giftName) }}</strong><span>{{ gift.giftCount }} 件</span></div>
              <div><b>{{ formatMilliYuan(gift.paidAmountMilliYuan) }}</b><em>{{ formatInsightPercent(gift.revenueShare) }}</em></div>
            </div>
          </div>
          <el-empty v-else :image-size="72" description="本场没有礼物或付费事件" />
        </el-card>
      </section>

      <el-card class="finding-card" shadow="never">
        <div class="card-heading compact">
          <div><h2>规则洞察</h2><p>结论来自公开口径的确定性规则，可直接回溯原始时间桶。</p></div>
          <span>{{ insight.findings.length }} 条</span>
        </div>
        <div class="finding-list">
          <article v-for="finding in insight.findings" :key="finding.code" :class="`finding--${finding.level.toLowerCase()}`">
            <el-tag size="small" :type="findingTagType(finding.level)" effect="light">{{ findingLevelLabel(finding.level) }}</el-tag>
            <div><strong>{{ finding.title }}</strong><p>{{ finding.description }}</p></div>
          </article>
        </div>
      </el-card>

      <section class="quality-strip">
        <div><span>采集覆盖</span><strong>{{ coverageLabel(insight.quality.coverageStatus) }}</strong></div>
        <div><span>支持事件</span><strong>{{ formatInteger(insight.quality.supportedEventCount) }}</strong></div>
        <div><span>身份解析事件占比</span><strong>{{ formatInsightPercent(insight.quality.identityResolvedEventShare) }}</strong></div>
        <div><span>事件延迟 P95</span><strong>{{ formatLatency(insight.quality.eventLatencyP95Millis) }}</strong></div>
      </section>
    </template>

    <el-card v-else class="empty-card" shadow="never" v-loading="loading">
      <el-empty description="请选择包含场次数据的直播间" />
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { fetchBilibiliLiveRooms, type BilibiliLiveRoom } from '@/api/bilibiliLive'
import {
  fetchBilibiliLiveSessionInsights,
  fetchBilibiliLiveSessions,
  formatMilliYuan,
  type BilibiliLiveSessionInsight,
  type BilibiliLiveSessionSummary
} from '@/api/bilibiliLiveSessions'
import TrendChart, { type TrendSeries } from '@/components/charts/TrendChart.vue'
import { useSessionAutoRefresh } from '@/views/bilibili-live/useSessionAutoRefresh'
import {
  formatInsightPercent,
  formatInsightRate,
  formatSecondsDuration,
  segmentScaleMaximum
} from './liveInsightPresentation'

type BucketSeconds = 60 | 300 | 900

const route = useRoute()
const loading = ref(false)
const rooms = ref<BilibiliLiveRoom[]>([])
const sessions = ref<BilibiliLiveSessionSummary[]>([])
const insight = ref<BilibiliLiveSessionInsight>()
const selectedMonitorId = ref<number>()
const selectedSessionId = ref<number>()
const bucketSeconds = ref<BucketSeconds>(300)
const autoRefresh = ref(false)
const refreshSeconds = ref(10)

const selectedSession = computed(() => sessions.value.find(item => item.id === selectedSessionId.value))
const timelineTimestamps = computed(() => insight.value?.timeline.map(item => item.bucketStart) ?? [])
const timelineLabels = computed(() => timelineTimestamps.value.map(formatTime))
const interactionSeries = computed<TrendSeries[]>(() => [
  { name: '弹幕', values: insight.value?.timeline.map(item => item.danmakuCount) ?? [], color: '#356df3' },
  { name: '活跃 UID', values: insight.value?.timeline.map(item => item.activeUserCount) ?? [], color: '#8b5cf6' }
])
const revenueSeries = computed<TrendSeries[]>(() => [
  { name: '付费金额（元）', values: insight.value?.timeline.map(item => item.paidAmountMilliYuan / 1000) ?? [], color: '#16a071' },
  { name: '付费事件', values: insight.value?.timeline.map(item => item.paidEventCount) ?? [], color: '#f59e0b' }
])
const timelineStart = computed(() => timelineTimestamps.value[0] ? formatTime(timelineTimestamps.value[0]) : '--')
const timelineEnd = computed(() => {
  const value = timelineTimestamps.value[timelineTimestamps.value.length - 1]
  return value ? formatTime(value) : '--'
})
const signalMarkers = computed(() => (insight.value?.peaks ?? []).map(peak => ({
  ...peak,
  position: peakPosition(peak.bucketStart)
})))
const identifiedSegmentUsers = computed(() => insight.value?.userSegments.reduce((sum, item) => sum + item.userCount, 0) ?? 0)
const segmentMaximum = computed(() => segmentScaleMaximum(insight.value?.userSegments ?? []))

const polling = useSessionAutoRefresh({
  refresh: loadInsight,
  intervalSeconds: refreshSeconds.value,
  onError: notifyError
})

onMounted(loadInitial)
onBeforeUnmount(polling.stop)

watch(selectedMonitorId, async (value, previous) => {
  if (value != null && previous != null && value !== previous) await loadSessions()
})
watch(selectedSessionId, async (value, previous) => {
  if (value != null && value !== previous) await loadInsight()
})
watch(bucketSeconds, () => { void loadInsight() })
watch(refreshSeconds, value => polling.setIntervalSeconds(value))
watch(autoRefresh, enabled => enabled ? polling.start() : polling.stop())

async function loadInitial() {
  loading.value = true
  try {
    rooms.value = await fetchBilibiliLiveRooms()
    const monitorFromRoute = numberQuery(route.query.monitorId)
    selectedMonitorId.value = rooms.value.some(room => room.id === monitorFromRoute)
      ? monitorFromRoute
      : rooms.value[0]?.id
    await loadSessions(false)
    await loadInsight(false)
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
    selectedSessionId.value = sessions.value.some(session => session.id === sessionFromRoute)
      ? sessionFromRoute
      : sessions.value[0]?.id
    if (toggleLoading) await loadInsight(false)
  } catch (error) {
    notifyError(error)
  } finally {
    if (toggleLoading) loading.value = false
  }
}

async function loadInsight(toggleLoading = true) {
  if (!selectedSessionId.value) {
    insight.value = undefined
    return
  }
  if (toggleLoading) loading.value = true
  try {
    insight.value = await fetchBilibiliLiveSessionInsights(selectedSessionId.value, bucketSeconds.value)
  } finally {
    if (toggleLoading) loading.value = false
  }
}

function peakPosition(value: string) {
  const timestamps = timelineTimestamps.value.map(item => Date.parse(item)).filter(Number.isFinite)
  if (timestamps.length < 2) return 50
  const start = timestamps[0]
  const end = timestamps[timestamps.length - 1]
  if (end <= start) return 50
  return Math.min(96, Math.max(4, ((Date.parse(value) - start) / (end - start)) * 100))
}

function segmentBarWidth(count: number) {
  return `${Math.max(count > 0 ? 6 : 0, (count / segmentMaximum.value) * 100)}%`
}

function percentBarWidth(value?: number | null) {
  if (value == null || !Number.isFinite(value)) return '0%'
  return `${Math.min(100, Math.max(value > 0 ? 4 : 0, value * 100))}%`
}

function formatDecimal(value?: number | null) {
  return value == null || !Number.isFinite(value) ? '--' : value.toFixed(2)
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

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

function formatInteger(value?: number | null) {
  return value == null ? '--' : new Intl.NumberFormat('zh-CN').format(value)
}

function formatLatency(value?: number | null) {
  return value == null ? '--' : `${new Intl.NumberFormat('zh-CN').format(value)} ms`
}

function sessionStateLabel(state: string) {
  return ({ CLOSED: '已结束', OPEN: '直播中', END_PENDING: '待确认', INCOMPLETE: '边界不完整' } as Record<string, string>)[state] || state
}

function coverageLabel(status: string) {
  return ({ RECEIVED_WHILE_ONLINE: '在线接收覆盖', BOUNDARY_ONLY: '仅历史边界', NO_ONLINE_COVERAGE: '无在线覆盖' } as Record<string, string>)[status] || status
}

function findingTagType(level: string) {
  if (level === 'RISK') return 'danger'
  if (level === 'OPPORTUNITY') return 'warning'
  return 'info'
}

function giftDisplayName(eventKind: string, giftName: string) {
  if (eventKind === 'SUPER_CHAT') return '醒目留言'
  if (eventKind === 'GUARD_BUY') return '大航海'
  return giftName
}

function findingLevelLabel(level: string) {
  return ({ RISK: '风险', OPPORTUNITY: '机会', INFO: '提示' } as Record<string, string>)[level] || level
}

function notifyError(error: unknown) {
  ElMessage.error(error instanceof Error ? error.message : '加载分析数据失败')
}
</script>

<style scoped>
.analytics-page { padding-bottom: 22px; }
.control-card, .signal-card, .chart-grid :deep(.el-card), .depth-card, .detail-card, .finding-card, .empty-card { border: 1px solid #e4e9f1; border-radius: 12px; }
.control-grid { display: grid; grid-template-columns: 260px minmax(300px, 1fr) 150px 240px; gap: 14px; align-items: end; }
.control-grid label { display: grid; gap: 7px; color: #667085; font-size: 12px; }
.refresh-control { display: flex; align-items: center; gap: 8px; }
.refresh-control :deep(.el-input-number) { width: 112px; }
.refresh-control em { font-style: normal; }
.coverage-alert { border-radius: 10px; }
.kpi-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.kpi-card { min-width: 0; padding: 18px; border: 1px solid #e5eaf2; border-radius: 12px; background: #fff; box-shadow: 0 8px 24px rgba(22, 34, 55, 0.035); }
.kpi-card--accent { border-color: #b9d1ff; background: linear-gradient(145deg, #f5f8ff, #fff); }
.kpi-card > span { color: #68778e; font-size: 13px; }
.kpi-card strong { display: block; overflow: hidden; margin-top: 10px; color: #172033; font-size: 24px; line-height: 1.1; text-overflow: ellipsis; white-space: nowrap; font-variant-numeric: tabular-nums; }
.kpi-card strong.money { color: #087c5a; }
.kpi-card small { display: block; margin-top: 9px; color: #98a3b4; font-size: 11px; line-height: 1.35; }
.card-heading { display: flex; align-items: end; justify-content: space-between; gap: 18px; margin-bottom: 18px; }
.card-heading.compact { margin-bottom: 12px; }
.card-heading h2 { margin: 0; font-size: 17px; }
.card-heading p { margin: 5px 0 0; color: #7a889d; font-size: 12px; line-height: 1.5; }
.card-heading > span { flex: 0 0 auto; color: #76859a; font-size: 12px; }
.signal-rail { position: relative; height: 102px; margin: 4px 14px 0; }
.rail-line { position: absolute; top: 41px; left: 0; right: 0; height: 2px; background: linear-gradient(90deg, #d9e4f5 0%, #9bb9f2 50%, #d9e4f5 100%); }
.rail-start, .rail-end { position: absolute; top: 58px; color: #8996aa; font-size: 11px; }
.rail-start { left: 0; }.rail-end { right: 0; }
.signal-marker { position: absolute; top: 18px; width: 1px; height: 44px; padding: 0; border: 0; background: none; color: #356df3; cursor: default; }
.signal-marker i { position: absolute; top: 16px; left: -5px; width: 11px; height: 11px; border: 3px solid #fff; border-radius: 50%; background: currentColor; box-shadow: 0 0 0 2px currentColor; }
.signal-marker b { position: absolute; left: 0; bottom: 33px; transform: translateX(-50%); color: currentColor; font-size: 11px; white-space: nowrap; }
.signal-marker--revenue { color: #16a071; }
.signal-rail > p { position: absolute; top: 52px; left: 50%; transform: translateX(-50%); margin: 0; color: #94a0b2; font-size: 12px; }
.chart-grid, .depth-grid, .detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.depth-card { min-width: 0; }
.depth-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.depth-metrics--four { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.depth-metrics article { min-width: 0; padding: 12px; border: 1px solid #e9edf4; border-radius: 9px; background: #f8fafc; }
.depth-metrics span, .depth-metrics small { display: block; overflow: hidden; color: #7d8b9f; font-size: 10px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.depth-metrics strong { display: block; overflow: hidden; margin: 7px 0 5px; color: #182235; font-size: 17px; line-height: 1.1; text-overflow: ellipsis; white-space: nowrap; font-variant-numeric: tabular-nums; }
.depth-section { margin-top: 17px; }
.section-label { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 9px; }
.section-label strong { font-size: 13px; }.section-label span { color: #8a96a8; font-size: 10px; }
.stage-list, .tier-list { display: grid; gap: 9px; }
.stage-list > div { display: grid; grid-template-columns: 42px minmax(70px, 1fr) 48px 54px; align-items: center; gap: 8px; }
.stage-list span, .stage-list b, .stage-list em, .tier-list span, .tier-list b, .tier-list em { font-size: 11px; font-style: normal; font-variant-numeric: tabular-nums; }
.stage-list b, .stage-list em, .tier-list b, .tier-list em { text-align: right; }.stage-list em, .tier-list span, .tier-list em { color: #8895a7; }
.depth-track { height: 7px; overflow: hidden; border-radius: 99px; background: #edf1f6; }
.depth-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #356df3, #8eabff); }
.depth-track--green i { background: linear-gradient(90deg, #0e956c, #6ac8a8); }
.repeated-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; }
.repeated-list > div { min-width: 0; padding: 9px 10px; border-radius: 8px; background: #f5f7fa; }
.repeated-list strong, .repeated-list span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.repeated-list strong { font-size: 12px; }.repeated-list span { margin-top: 4px; color: #8b97a8; font-size: 10px; }
.depth-empty, .method-note { margin: 0; color: #8b97a8; font-size: 10px; line-height: 1.55; }
.tier-list > div { display: grid; grid-template-columns: 92px minmax(70px, 1fr) 88px 48px; align-items: center; gap: 8px; }
.tier-list > div > div:first-child { display: flex; align-items: baseline; gap: 6px; min-width: 0; }
.tier-list strong { font-size: 11px; white-space: nowrap; }
.method-note { margin-top: 16px; padding: 9px 11px; border-radius: 8px; background: #f5faf8; color: #658277; }
.bar-list { display: grid; gap: 15px; }
.bar-row { display: grid; grid-template-columns: 150px minmax(80px, 1fr) 34px; align-items: center; gap: 12px; }
.bar-row > div:first-child { min-width: 0; }
.bar-row strong, .bar-row span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-row strong { font-size: 13px; }.bar-row span { margin-top: 3px; color: #8b98aa; font-size: 10px; }
.bar-track { height: 8px; overflow: hidden; border-radius: 99px; background: #edf1f6; }
.bar-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #356df3, #7c9fff); }
.bar-row > b { text-align: right; font-size: 13px; font-variant-numeric: tabular-nums; }
.gift-list { display: grid; gap: 3px; }
.gift-list > div { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 11px 4px; border-bottom: 1px solid #eef1f5; }
.gift-list > div:last-child { border-bottom: 0; }
.gift-list div div { display: flex; align-items: baseline; gap: 9px; min-width: 0; }
.gift-list strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.gift-list span, .gift-list em { color: #8c99aa; font-size: 11px; font-style: normal; }
.gift-list b { color: #087c5a; font-size: 13px; font-variant-numeric: tabular-nums; }
.finding-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.finding-list article { display: flex; align-items: flex-start; gap: 10px; padding: 14px; border: 1px solid #e8edf4; border-radius: 10px; background: #fafbfd; }
.finding-list strong { font-size: 13px; }.finding-list p { margin: 5px 0 0; color: #748298; font-size: 12px; line-height: 1.55; }
.finding--risk { border-color: #f3cccc !important; background: #fffafa !important; }
.finding--opportunity { border-color: #f1dfbd !important; background: #fffdf7 !important; }
.quality-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); overflow: hidden; border: 1px solid #dde5ef; border-radius: 12px; background: #eef3f8; }
.quality-strip div { padding: 14px 18px; border-right: 1px solid #dce4ed; }
.quality-strip div:last-child { border-right: 0; }
.quality-strip span { display: block; color: #78869b; font-size: 11px; }.quality-strip strong { display: block; margin-top: 6px; font-size: 14px; }
.empty-card { min-height: 360px; display: grid; place-items: center; }
@media (max-width: 1280px) {
  .control-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .kpi-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .chart-grid, .depth-grid, .detail-grid { grid-template-columns: 1fr; }
  .finding-list { grid-template-columns: 1fr; }
  .quality-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .control-grid, .kpi-grid, .quality-strip { grid-template-columns: 1fr; }
  .depth-metrics, .depth-metrics--four { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .bar-row { grid-template-columns: 120px minmax(60px, 1fr) 28px; }
}
</style>
