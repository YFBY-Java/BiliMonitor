import { getData } from './http'

export interface TrendPoint {
  date: string
  value: number
}

export interface SystemOverview {
  platformCount: number
  enabledTaskCount: number
  todaySuccessCount: number
  todayFailedCount: number
  followerTrend: TrendPoint[]
}

export async function fetchSystemOverview(): Promise<SystemOverview> {
  try {
    return await getData<SystemOverview>('/api/dev/overview')
  } catch {
    return {
      platformCount: 1,
      enabledTaskCount: 0,
      todaySuccessCount: 0,
      todayFailedCount: 0,
      followerTrend: [
        { date: 'Mon', value: 1200 },
        { date: 'Tue', value: 1280 },
        { date: 'Wed', value: 1330 },
        { date: 'Thu', value: 1410 },
        { date: 'Fri', value: 1520 }
      ]
    }
  }
}

