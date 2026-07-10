import { deleteData, getData, patchData, postData, putData } from './http'

export interface SubjectBilibiliBinding {
  id: number
  subjectId: number
  bilibiliUserMonitorId?: number
  bilibiliLiveRoomMonitorId?: number
  mid?: number
  roomId?: number
  enabledCapabilities: string[]
  danmuEnabled: boolean
  createdAt: string
  updatedAt: string
}

export interface Subject {
  id: number
  displayName: string
  avatarUrl?: string
  remark?: string
  tags: string[]
  monitorStatus: 'ACTIVE' | 'PAUSED'
  healthScore?: number
  lastSuccessAt?: string
  lastEventAt?: string
  createdAt: string
  updatedAt: string
  bilibiliBinding?: SubjectBilibiliBinding
}

export interface CreateSubjectBody {
  displayName?: string
  avatarUrl?: string
  remark?: string
  tags?: string[]
  bilibiliUserMonitorId?: number
  bilibiliLiveRoomMonitorId?: number
  mid?: number
  roomId?: number
  danmuEnabled?: boolean
}

export interface UpdateSubjectBody {
  displayName?: string
  avatarUrl?: string
  remark?: string
  tags?: string[]
  enabled?: boolean
}

export interface UpdateSubjectBilibiliBindingBody {
  bilibiliUserMonitorId?: number
  bilibiliLiveRoomMonitorId?: number
  mid?: number
  roomId?: number
  enabledCapabilities?: string[]
  danmuEnabled?: boolean
}

export interface SubjectBilibiliUser {
  monitorId: number
  mid: number
  nickname: string
  avatarUrl?: string
  profileUrl?: string
  followerCount?: number
  followingCount?: number
  followerDelta24h?: number
  monitorStatus: 'ACTIVE' | 'PAUSED'
  lastSuccessAt?: string
  nextCollectAt?: string
  lastErrorType?: string
  lastErrorMessage?: string
}

export interface SubjectBilibiliLiveRoom {
  monitorId: number
  uid: number
  roomId: number
  uname: string
  faceUrl?: string
  title?: string
  coverUrl?: string
  keyframeUrl?: string
  areaName?: string
  parentAreaName?: string
  liveStatus?: 0 | 1 | 2
  liveTime?: string
  onlineCount?: number
  onlineDelta24h?: number
  onlinePeak24h?: number
  monitorStatus: 'ACTIVE' | 'PAUSED'
  lastSuccessAt?: string
  nextCollectAt?: string
  lastErrorType?: string
  lastErrorMessage?: string
}

export interface SubjectSummary {
  followerCount?: number
  followerDelta24h?: number
  liveStatus?: 0 | 1 | 2
  onlineCount?: number
  onlineDelta24h?: number
  onlinePeak24h?: number
  danmuPerMinute?: number
  danmuLast5Minutes?: number
  healthScore?: number
  enabledModuleCount: number
  totalModuleCount: number
  lastSuccessAt?: string
  nextCollectAt?: string
}

export interface SubjectDanmuRecentMessage {
  displayName?: string
  messageText: string
  medalName?: string
  sentAt: string
}

export interface SubjectDanmu {
  enabled: boolean
  status: 'disabled' | 'waiting' | 'connected' | 'stopped' | 'error' | 'missing_live_room' | string
  ratePerMinute?: number
  last5MinutesCount?: number
  likeIncrement?: number
  watchedCount?: number
  lastMessageAt?: string
  recentMessages: SubjectDanmuRecentMessage[]
}

export interface SubjectWidgetLayout {
  widgetKey: string
  enabled: boolean
  position: Record<string, unknown>
  settings: Record<string, unknown>
}

export interface SubjectHealthEvent {
  eventType: string
  title: string
  description?: string
  source: string
  occurredAt: string
  level: 'info' | 'success' | 'warning' | 'error'
}

export interface SubjectWorkbench {
  subject: Subject
  bilibiliBinding?: SubjectBilibiliBinding
  bilibiliUser?: SubjectBilibiliUser
  bilibiliLiveRoom?: SubjectBilibiliLiveRoom
  summary: SubjectSummary
  danmu: SubjectDanmu
  layout: SubjectWidgetLayout[]
  recentEvents: SubjectHealthEvent[]
}

export interface SubjectTrendPoint {
  bucketAt: string
  followerCount?: number
  liveOnlineCount?: number
}

export interface SubjectTrend {
  subjectId: number
  metrics: string[]
  range: string
  bucket: string
  points: SubjectTrendPoint[]
}

export interface SubjectLayoutItemBody {
  widgetKey: string
  enabled?: boolean
  position?: Record<string, unknown>
  settings?: Record<string, unknown>
}

export async function fetchSubjects(): Promise<Subject[]> {
  return getData<Subject[]>('/api/subjects')
}

export async function createSubject(body: CreateSubjectBody): Promise<Subject> {
  return postData<Subject, CreateSubjectBody>('/api/subjects', body)
}

export async function fetchSubject(subjectId: number): Promise<Subject> {
  return getData<Subject>(`/api/subjects/${subjectId}`)
}

export async function updateSubject(subjectId: number, body: UpdateSubjectBody): Promise<Subject> {
  return patchData<Subject, UpdateSubjectBody>(`/api/subjects/${subjectId}`, body)
}

export async function deleteSubject(subjectId: number): Promise<void> {
  await deleteData(`/api/subjects/${subjectId}`)
}

export async function bindSubjectBilibili(
  subjectId: number,
  body: UpdateSubjectBilibiliBindingBody
): Promise<SubjectBilibiliBinding> {
  return postData<SubjectBilibiliBinding, UpdateSubjectBilibiliBindingBody>(
    `/api/subjects/${subjectId}/bilibili-binding`,
    body
  )
}

export async function updateSubjectBilibiliBinding(
  subjectId: number,
  body: UpdateSubjectBilibiliBindingBody
): Promise<SubjectBilibiliBinding> {
  return patchData<SubjectBilibiliBinding, UpdateSubjectBilibiliBindingBody>(
    `/api/subjects/${subjectId}/bilibili-binding`,
    body
  )
}

export async function fetchSubjectWorkbench(subjectId: number): Promise<SubjectWorkbench> {
  return getData<SubjectWorkbench>(`/api/subjects/${subjectId}/workbench`)
}

export async function fetchSubjectTrends(
  subjectId: number,
  metrics = 'follower,live_online',
  range = '24h',
  bucket = '5m'
): Promise<SubjectTrend> {
  const query = new URLSearchParams({ metrics, range, bucket })
  return getData<SubjectTrend>(`/api/subjects/${subjectId}/trends?${query.toString()}`)
}

export async function updateSubjectLayout(subjectId: number, body: SubjectLayoutItemBody[]): Promise<SubjectWidgetLayout[]> {
  return putData<SubjectWidgetLayout[], SubjectLayoutItemBody[]>(`/api/subjects/${subjectId}/layout`, body)
}
