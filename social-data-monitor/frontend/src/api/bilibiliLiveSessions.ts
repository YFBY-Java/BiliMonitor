import { getData } from './http'

const BASE_URL = '/api/bilibili/live-monitor'
const EXPORT_CATEGORIES = ['danmaku', 'gifts', 'users', 'xlsx', 'all'] as const

export type BilibiliLiveSessionExportCategory = (typeof EXPORT_CATEGORIES)[number]
export type BilibiliLiveSessionCoverageStatus =
  | 'BOUNDARY_ONLY'
  | 'NO_ONLINE_COVERAGE'
  | 'RECEIVED_WHILE_ONLINE'

export const BILIBILI_LIVE_SESSION_IDENTITY_METRIC_LABELS = {
  giftSender: '已识别送礼用户',
  paidUser: '已识别付费用户',
  interactingUser: '已识别互动用户'
} as const

export interface BilibiliLiveSessionSummary {
  id: number
  monitorId: number
  uid: number
  roomId: number
  state: string
  startedAt: string
  endedAt?: string | null
  startSource?: string | null
  endSource?: string | null
  coverageStatus: BilibiliLiveSessionCoverageStatus
  transportSessionCount: number
  captureStartedAt?: string | null
  captureEndedAt?: string | null
  danmakuCount: number | null
  giftEventCount: number | null
  giftCount: number | null
  freeGiftCount: number | null
  giftSenderCount: number | null
  paidUserCount: number | null
  interactingUserCount: number | null
  unresolvedInteractingEventCount: number | null
  unresolvedGiftEventCount: number | null
  unresolvedPaidEventCount: number | null
  paidEventCount: number | null
  paidAmountMilliYuan: number | null
  firstEventAt?: string | null
  lastEventAt?: string | null
}

export interface BilibiliLiveSessionDetail extends BilibiliLiveSessionSummary {}

export interface BilibiliLiveSessionUser {
  actorKey: string
  identityQuality: 'VERIFIED_UID' | 'UNRESOLVED_EVENT'
  userUid?: number | null
  displayName?: string | null
  danmakuCount: number
  giftEventCount: number
  giftCount: number
  freeGiftCount: number
  paidEventCount: number
  paidAmountMilliYuan: number
  firstSeenAt?: string | null
  lastSeenAt?: string | null
}

export function buildBilibiliLiveSessionsUrl(monitorId: number, limit = 20): string {
  return `${BASE_URL}/rooms/${positiveIntegerSegment(monitorId, 'monitorId')}/sessions?${new URLSearchParams({
    limit: String(positiveInteger(limit, 'limit'))
  })}`
}

export function buildBilibiliLiveSessionUrl(sessionId: number): string {
  return `${BASE_URL}/sessions/${positiveIntegerSegment(sessionId, 'sessionId')}`
}

export function buildBilibiliLiveSessionUsersUrl(sessionId: number, limit = 100): string {
  return `${buildBilibiliLiveSessionUrl(sessionId)}/users?${new URLSearchParams({
    limit: String(positiveInteger(limit, 'limit'))
  })}`
}

export function isBilibiliLiveSessionExportCategory(
  value: unknown
): value is BilibiliLiveSessionExportCategory {
  return typeof value === 'string' && EXPORT_CATEGORIES.some(category => category === value)
}

export function isBilibiliLiveSessionEndPending(state?: string | null): boolean {
  return state?.trim().toUpperCase() === 'END_PENDING'
}

export function bilibiliLiveSessionEndFallback(state?: string | null, endedAt?: string | null): string {
  if (endedAt) return ''
  return state?.trim().toUpperCase() === 'INCOMPLETE' ? '结束时间未知' : '进行中'
}

export function isBilibiliLiveSessionDurationUnknown(
  state?: string | null,
  endedAt?: string | null
): boolean {
  return !endedAt && state?.trim().toUpperCase() === 'INCOMPLETE'
}

export function bilibiliLiveSessionEndPendingCaveat(): string {
  return '等待下一次 REST 状态复核；确认下播后关闭。'
}

export function bilibiliLiveSessionIdentityListCaveat(): string {
  return '最多 100 条用户或未解析身份记录。'
}

export function bilibiliLiveSessionIdentityMetricsCaveat(): string {
  return '已识别用户仅统计正 UID；未解析身份按事件计数，不代表人数。'
}

export function bilibiliLiveSessionCaptureScopeCaveat(): string {
  return '记录范围仅限本功能部署后，WebSocket 在线期间成功解析并持久化的受支持事件/记录；部署前、断线、未知/畸形帧及持久化失败的数据不在记录范围内。'
}

export function bilibiliLiveSessionCoverageCaveat(
  status?: BilibiliLiveSessionCoverageStatus | null
): string {
  if (status === 'BOUNDARY_ONLY') {
    return '历史边界记录：没有可验证的在线成功解析并持久化区间；空白指标不能解释为 0。'
  }
  if (status === 'RECEIVED_WHILE_ONLINE') {
    return '仅覆盖 WebSocket 在线期间成功解析并持久化的受支持事件/记录；不代表平台全量，也不包含未知/畸形帧或持久化失败的数据。'
  }
  return '无在线采集区间：没有可验证的成功解析并持久化记录，当前指标不具备完整覆盖保证。'
}

export function buildBilibiliLiveSessionExportUrl(sessionId: number, category: string): string {
  if (!isBilibiliLiveSessionExportCategory(category)) {
    throw new Error(`Unsupported export category: ${category}`)
  }
  return `${buildBilibiliLiveSessionUrl(sessionId)}/export?${new URLSearchParams({ category })}`
}

export function formatMilliYuan(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return '--'
  const yuan = value / 1000
  return `${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 3
  }).format(yuan)} 元`
}

export function fetchBilibiliLiveSessions(
  monitorId: number,
  limit = 20
): Promise<BilibiliLiveSessionSummary[]> {
  return getData<BilibiliLiveSessionSummary[]>(buildBilibiliLiveSessionsUrl(monitorId, limit))
}

export function fetchBilibiliLiveSession(sessionId: number): Promise<BilibiliLiveSessionDetail> {
  return getData<BilibiliLiveSessionDetail>(buildBilibiliLiveSessionUrl(sessionId))
}

export function fetchBilibiliLiveSessionUsers(
  sessionId: number,
  limit = 100
): Promise<BilibiliLiveSessionUser[]> {
  return getData<BilibiliLiveSessionUser[]>(buildBilibiliLiveSessionUsersUrl(sessionId, limit))
}

function positiveIntegerSegment(value: number, name: string): string {
  return encodeURIComponent(String(positiveInteger(value, name)))
}

function positiveInteger(value: number, name: string): number {
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer`)
  }
  return value
}
