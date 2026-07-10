import { deleteData, getData, patchData, postData } from './http'

export interface BilibiliFollowerPoint {
  capturedAt: string
  followerCount: number
  followingCount?: number
  sourceEndpoint: string
}

export interface BilibiliMonitorUser {
  id: number
  mid: number
  nickname: string
  avatarUrl?: string
  profileUrl: string
  currentFollowerCount?: number
  followingCount?: number
  deltaSincePrevious?: number
  growthRateSincePrevious?: number
  lastSnapshotAt?: string
  lastSuccessAt?: string
  nextCollectAt?: string
  monitorStatus: 'ACTIVE' | 'PAUSED'
  intervalSeconds: number
  lastErrorType?: string
  lastErrorMessage?: string
  lastErrorAt?: string
  sourceEndpoint?: string
  recentTrend: BilibiliFollowerPoint[]
}

export interface BilibiliUserTrend {
  user: BilibiliMonitorUser
  points: BilibiliFollowerPoint[]
}

export interface BilibiliCollectResult {
  userId: number
  mid: number
  success: boolean
  followerCount?: number
  capturedAt: string
  sourceEndpoint?: string
  message: string
}

export interface AddBilibiliMonitorUserBody {
  mid: number
  intervalSeconds?: number
}

export interface UpdateBilibiliMonitorSettingsBody {
  intervalSeconds: number
}

export async function fetchBilibiliMonitorUsers(): Promise<BilibiliMonitorUser[]> {
  return getData<BilibiliMonitorUser[]>('/api/bilibili/follower-monitor/users')
}

export async function addBilibiliMonitorUser(body: AddBilibiliMonitorUserBody): Promise<BilibiliMonitorUser> {
  return postData<BilibiliMonitorUser, AddBilibiliMonitorUserBody>('/api/bilibili/follower-monitor/users', body)
}

export async function updateBilibiliMonitorStatus(userId: number, enabled: boolean): Promise<BilibiliMonitorUser> {
  return patchData<BilibiliMonitorUser, { enabled: boolean }>(
    `/api/bilibili/follower-monitor/users/${userId}/status`,
    { enabled }
  )
}

export async function updateBilibiliMonitorSettings(
  userId: number,
  body: UpdateBilibiliMonitorSettingsBody
): Promise<BilibiliMonitorUser> {
  return patchData<BilibiliMonitorUser, UpdateBilibiliMonitorSettingsBody>(
    `/api/bilibili/follower-monitor/users/${userId}/settings`,
    body
  )
}

export async function refreshBilibiliMonitorUser(userId: number): Promise<BilibiliCollectResult> {
  return postData<BilibiliCollectResult, Record<string, never>>(
    `/api/bilibili/follower-monitor/users/${userId}/refresh`,
    {}
  )
}

export async function deleteBilibiliMonitorUser(userId: number): Promise<void> {
  await deleteData(`/api/bilibili/follower-monitor/users/${userId}`)
}

export async function fetchBilibiliUserTrend(userId: number, limit = 500): Promise<BilibiliUserTrend> {
  return getData<BilibiliUserTrend>(`/api/bilibili/follower-monitor/users/${userId}/history?limit=${limit}`)
}

export async function fetchBilibiliTrends(userIds: number[], limitPerUser = 500): Promise<BilibiliUserTrend[]> {
  const query = userIds.map((id) => `userIds=${encodeURIComponent(id)}`).join('&')
  const prefix = query ? `${query}&` : ''
  return getData<BilibiliUserTrend[]>(`/api/bilibili/follower-monitor/trends?${prefix}limitPerUser=${limitPerUser}`)
}
