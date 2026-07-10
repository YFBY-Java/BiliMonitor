import { deleteData, getData, http, patchData, postData, type ApiResponse } from './http'

export type LiveStatus = 0 | 1 | 2
export type LiveMonitorStatus = 'ACTIVE' | 'PAUSED'

export interface BilibiliLiveTrendPoint {
  roomId: number
  uid: number
  capturedAt: string
  liveStatus: LiveStatus
  onlineCount?: number
  attentionCount?: number
  sourceEndpoint?: string
}

export interface BilibiliLiveRoom {
  id: number
  uid: number
  roomId: number
  shortId?: number
  uname: string
  faceUrl?: string
  title?: string
  coverUrl?: string
  keyframeUrl?: string
  areaId?: number
  areaName?: string
  parentAreaId?: number
  parentAreaName?: string
  liveStatus: LiveStatus
  liveTime?: string
  onlineCount?: number
  attentionCount?: number
  onlineDelta?: number
  monitorStatus: LiveMonitorStatus
  intervalSeconds: number
  nextCollectAt?: string
  lastSnapshotAt?: string
  lastSuccessAt?: string
  lastErrorAt?: string
  lastErrorType?: string
  lastErrorMessage?: string
  backoffUntil?: string
  sourceEndpoint?: string
  recentTrend: BilibiliLiveTrendPoint[]
}

export interface BilibiliLiveStatusEvent {
  id: number
  monitorId: number
  uid: number
  roomId: number
  eventType: string
  fromLiveStatus?: LiveStatus
  toLiveStatus?: LiveStatus
  titleBefore?: string
  titleAfter?: string
  onlineCount?: number
  occurredAt: string
}

export interface BilibiliLiveSummary {
  totalRooms: number
  activeRooms: number
  liveRooms: number
  roundRooms: number
  offlineRooms: number
  errorRooms: number
  totalOnlineCount: number
  todayLiveStarts: number
  latestEvent?: BilibiliLiveStatusEvent
}

export interface BilibiliLiveRoomTrend {
  room: BilibiliLiveRoom
  points: BilibiliLiveTrendPoint[]
}

export interface AddBilibiliLiveRoomBody {
  uid?: number
  roomId?: number
  intervalSeconds?: number
}

export interface UpdateBilibiliLiveRoomBody {
  intervalSeconds?: number
  enabled?: boolean
}

export interface BilibiliLiveCollectResult {
  roomMonitorId: number
  uid: number
  roomId: number
  success: boolean
  liveStatus?: LiveStatus
  onlineCount?: number
  capturedAt: string
  sourceEndpoint?: string
  message: string
}

export interface BilibiliLiveDanmakuStatus {
  liveRoomMonitorId: number
  roomId?: number
  running: boolean
  status: string
  connectHost?: string
  sessionId?: number
  ratePerMinute?: number
  last5MinutesCount?: number
  likeCount?: number
  likeIncrement?: number
  watchedCount?: number
  heartbeatPopularity?: number
  protocolVersion?: number
  startedAt?: string
  lastHeartbeatAt?: string
  lastErrorAt?: string
  lastErrorType?: string
  lastErrorMessage?: string
}

export interface BilibiliLiveDanmakuRecent {
  messageText: string
  displayName?: string
  medalName?: string
  sentAt: string
}

export interface BilibiliLiveDanmakuMetricBucket {
  bucketStart: string
  bucketSeconds: number
  danmuCount: number
  likeCount?: number
  likeIncrement?: number
  watchedCount?: number
  heartbeatPopularity?: number
  rawEventCount: number
}

export interface BilibiliLiveRankEntry {
  userUid?: number
  rankNo?: number
  entryKind: string
  displayName?: string
  faceUrl?: string
  score?: number
  guardLevel?: number
  wealthLevel?: number
  medalName?: string
  medalLevel?: number
  guardExpiredText?: string
  accompanyDays?: number
}

export interface BilibiliLiveRankSnapshot {
  id: number
  roomMonitorId: number
  roomId: number
  ruid: number
  rankFamily: 'AUDIENCE' | 'GUARD'
  rankType: string
  rankSwitch?: string
  periodScope?: string
  pageNo: number
  pageSize: number
  totalCount?: number
  countText?: string
  valueText?: string
  remindMsg?: string
  sourceEndpoint?: string
  signedRequired: boolean
  capturedAt: string
  entries: BilibiliLiveRankEntry[]
}

export interface BilibiliLiveRankSummary {
  roomMonitorId: number
  roomId: number
  ruid: number
  audienceCount?: number
  audienceCountText?: string
  guardCount?: number
  guardCountText?: string
  updatedAt?: string
  snapshots: BilibiliLiveRankSnapshot[]
}

export interface RefreshBilibiliLiveRanksBody {
  families?: Array<'AUDIENCE' | 'GUARD'>
  types?: string[]
  maxPages?: number
  force?: boolean
}

export interface BilibiliLiveRankRefreshResult {
  roomMonitorId: number
  successCount: number
  errors: string[]
  summary: BilibiliLiveRankSummary
}

export async function fetchBilibiliLiveRooms(): Promise<BilibiliLiveRoom[]> {
  return getData<BilibiliLiveRoom[]>('/api/bilibili/live-monitor/rooms')
}

export async function fetchBilibiliLiveSummary(): Promise<BilibiliLiveSummary> {
  return getData<BilibiliLiveSummary>('/api/bilibili/live-monitor/summary')
}

export async function addBilibiliLiveRoom(body: AddBilibiliLiveRoomBody): Promise<BilibiliLiveRoom> {
  return postData<BilibiliLiveRoom, AddBilibiliLiveRoomBody>('/api/bilibili/live-monitor/rooms', body)
}

export async function updateBilibiliLiveRoom(
  roomId: number,
  body: UpdateBilibiliLiveRoomBody
): Promise<BilibiliLiveRoom> {
  return patchData<BilibiliLiveRoom, UpdateBilibiliLiveRoomBody>(
    `/api/bilibili/live-monitor/rooms/${roomId}`,
    body
  )
}

export async function refreshBilibiliLiveRoom(roomId: number): Promise<BilibiliLiveCollectResult> {
  return postData<BilibiliLiveCollectResult, Record<string, never>>(
    `/api/bilibili/live-monitor/rooms/${roomId}/refresh`,
    {}
  )
}

export async function deleteBilibiliLiveRoom(roomId: number): Promise<void> {
  await deleteData(`/api/bilibili/live-monitor/rooms/${roomId}`)
}

export async function fetchBilibiliLiveTrends(roomIds: number[], limitPerRoom = 500): Promise<BilibiliLiveRoomTrend[]> {
  const query = roomIds.map((id) => `roomIds=${encodeURIComponent(id)}`).join('&')
  const prefix = query ? `${query}&` : ''
  return getData<BilibiliLiveRoomTrend[]>(`/api/bilibili/live-monitor/trends?${prefix}limitPerRoom=${limitPerRoom}`)
}

export async function fetchBilibiliLiveEvents(limit = 20): Promise<BilibiliLiveStatusEvent[]> {
  return getData<BilibiliLiveStatusEvent[]>(`/api/bilibili/live-monitor/events?limit=${limit}`)
}

export async function startBilibiliLiveDanmaku(
  roomId: number,
  protocolVersion?: number
): Promise<BilibiliLiveDanmakuStatus> {
  const query = protocolVersion == null ? '' : `?protocolVersion=${encodeURIComponent(protocolVersion)}`
  return postData<BilibiliLiveDanmakuStatus, Record<string, never>>(
    `/api/bilibili/live-monitor/rooms/${roomId}/danmaku/start${query}`,
    {}
  )
}

export async function stopBilibiliLiveDanmaku(roomId: number): Promise<BilibiliLiveDanmakuStatus> {
  return postData<BilibiliLiveDanmakuStatus, Record<string, never>>(
    `/api/bilibili/live-monitor/rooms/${roomId}/danmaku/stop`,
    {}
  )
}

export async function fetchBilibiliLiveDanmakuStatus(roomId: number): Promise<BilibiliLiveDanmakuStatus> {
  return getData<BilibiliLiveDanmakuStatus>(`/api/bilibili/live-monitor/rooms/${roomId}/danmaku/status`)
}

export async function fetchBilibiliLiveDanmakuRecent(
  roomId: number,
  limit = 30
): Promise<BilibiliLiveDanmakuRecent[]> {
  return getData<BilibiliLiveDanmakuRecent[]>(
    `/api/bilibili/live-monitor/rooms/${roomId}/danmaku/recent?limit=${limit}`
  )
}

export async function fetchBilibiliLiveDanmakuMetrics(
  roomId: number,
  range = '1h'
): Promise<BilibiliLiveDanmakuMetricBucket[]> {
  return getData<BilibiliLiveDanmakuMetricBucket[]>(
    `/api/bilibili/live-monitor/rooms/${roomId}/danmaku/metrics?range=${encodeURIComponent(range)}`
  )
}

export async function fetchBilibiliLiveRankSummary(roomId: number): Promise<BilibiliLiveRankSummary> {
  return getData<BilibiliLiveRankSummary>(`/api/bilibili/live-monitor/rooms/${roomId}/ranks/summary`)
}

export async function refreshBilibiliLiveRanks(
  roomId: number,
  body: RefreshBilibiliLiveRanksBody = { maxPages: 1 }
): Promise<BilibiliLiveRankRefreshResult> {
  const response = await http.post<ApiResponse<BilibiliLiveRankRefreshResult>>(
    `/api/bilibili/live-monitor/rooms/${roomId}/ranks/refresh`,
    body,
    { timeout: 120000 }
  )
  return response.data.data
}
