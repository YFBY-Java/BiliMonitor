import { describe, expect, it } from 'vitest'

import {
  BILIBILI_LIVE_SESSION_IDENTITY_METRIC_LABELS,
  buildBilibiliLiveSessionExportUrl,
  buildBilibiliLiveSessionUrl,
  buildBilibiliLiveSessionUsersUrl,
  buildBilibiliLiveSessionsUrl,
  bilibiliLiveSessionCoverageCaveat,
  formatMilliYuan,
  bilibiliLiveSessionEndPendingCaveat,
  bilibiliLiveSessionIdentityListCaveat,
  bilibiliLiveSessionEndFallback,
  isBilibiliLiveSessionDurationUnknown,
  bilibiliLiveSessionIdentityMetricsCaveat,
  bilibiliLiveSessionCaptureScopeCaveat,
  isBilibiliLiveSessionEndPending,
  isBilibiliLiveSessionExportCategory
} from './bilibiliLiveSessions'

describe('Bilibili live session helpers', () => {
  it('encodes room-session, detail, and user query URLs', () => {
    expect(buildBilibiliLiveSessionsUrl(7, 20)).toBe(
      '/api/bilibili/live-monitor/rooms/7/sessions?limit=20'
    )
    expect(buildBilibiliLiveSessionUrl(42)).toBe(
      '/api/bilibili/live-monitor/sessions/42'
    )
    expect(buildBilibiliLiveSessionUsersUrl(42, 100)).toBe(
      '/api/bilibili/live-monitor/sessions/42/users?limit=100'
    )
  })

  it('builds an encoded export URL for every supported category', () => {
    expect(buildBilibiliLiveSessionExportUrl(42, 'danmaku')).toBe(
      '/api/bilibili/live-monitor/sessions/42/export?category=danmaku'
    )
    expect(buildBilibiliLiveSessionExportUrl(42, 'gifts')).toBe(
      '/api/bilibili/live-monitor/sessions/42/export?category=gifts'
    )
    expect(buildBilibiliLiveSessionExportUrl(42, 'users')).toBe(
      '/api/bilibili/live-monitor/sessions/42/export?category=users'
    )
    expect(buildBilibiliLiveSessionExportUrl(42, 'all')).toBe(
      '/api/bilibili/live-monitor/sessions/42/export?category=all'
    )
  })

  it('rejects categories outside the export whitelist', () => {
    expect(isBilibiliLiveSessionExportCategory('users')).toBe(true)
    expect(isBilibiliLiveSessionExportCategory('users&admin=true')).toBe(false)
    expect(() => buildBilibiliLiveSessionExportUrl(42, 'users&admin=true')).toThrow('Unsupported export category')
  })

  it('rejects invalid identifiers and limits before building a URL', () => {
    expect(() => buildBilibiliLiveSessionsUrl(0, 20)).toThrow('monitorId must be a positive integer')
    expect(() => buildBilibiliLiveSessionUrl(Number.NaN)).toThrow('sessionId must be a positive integer')
    expect(() => buildBilibiliLiveSessionUsersUrl(42, 0)).toThrow('limit must be a positive integer')
  })

  it('converts milli-yuan to a yuan display value without discarding milli precision', () => {
    expect(formatMilliYuan(12_345)).toBe('12.345 元')
    expect(formatMilliYuan(12_000)).toBe('12.00 元')
    expect(formatMilliYuan(0)).toBe('0.00 元')
    expect(formatMilliYuan(undefined)).toBe('--')
  })

  it('recognizes the end-confirmation state without treating closed sessions as pending', () => {
    expect(isBilibiliLiveSessionEndPending('END_PENDING')).toBe(true)
    expect(isBilibiliLiveSessionEndPending(' end_pending ')).toBe(true)
    expect(isBilibiliLiveSessionEndPending('CLOSED')).toBe(false)
    expect(bilibiliLiveSessionEndPendingCaveat()).toBe('等待下一次 REST 状态复核；确认下播后关闭。')
  })

  it('explains historical boundary and online capture coverage truthfully', () => {
    expect(bilibiliLiveSessionCoverageCaveat('BOUNDARY_ONLY')).toContain('历史边界')
    expect(bilibiliLiveSessionCoverageCaveat('BOUNDARY_ONLY')).toContain('不能解释为 0')
    expect(bilibiliLiveSessionCoverageCaveat('NO_ONLINE_COVERAGE')).toContain('无在线采集区间')
    expect(bilibiliLiveSessionCoverageCaveat('RECEIVED_WHILE_ONLINE')).toContain('WebSocket 在线期间')
  })

  it('limits capture claims to supported events that were parsed and persisted successfully', () => {
    const scope = bilibiliLiveSessionCaptureScopeCaveat()
    const onlineCoverage = bilibiliLiveSessionCoverageCaveat('RECEIVED_WHILE_ONLINE')

    expect(scope).toContain('WebSocket 在线期间成功解析并持久化的受支持事件/记录')
    expect(scope).toContain('未知/畸形帧')
    expect(scope).toContain('持久化失败')
    expect(onlineCoverage).toContain('WebSocket 在线期间成功解析并持久化的受支持事件/记录')
    expect(onlineCoverage).toContain('不代表平台全量')
    expect(onlineCoverage).not.toContain('实际收到')
    expect(onlineCoverage).not.toContain('完整全量')
  })

  it('describes the mixed verified and unresolved identity rows accurately', () => {
    expect(bilibiliLiveSessionIdentityListCaveat()).toBe('最多 100 条用户或未解析身份记录。')
  })

  it('labels verified UID metrics separately from unresolved event counts', () => {
    expect(BILIBILI_LIVE_SESSION_IDENTITY_METRIC_LABELS).toEqual({
      giftSender: '已识别送礼用户',
      paidUser: '已识别付费用户',
      interactingUser: '已识别互动用户'
    })
    expect(bilibiliLiveSessionIdentityMetricsCaveat()).toContain('仅统计正 UID')
    expect(bilibiliLiveSessionIdentityMetricsCaveat()).toContain('未解析身份按事件计数')
  })

  it('does not present an incomplete historical boundary as an ongoing session', () => {
    expect(bilibiliLiveSessionEndFallback('INCOMPLETE', null)).toBe('结束时间未知')
    expect(isBilibiliLiveSessionDurationUnknown('INCOMPLETE', null)).toBe(true)
    expect(bilibiliLiveSessionEndFallback('OPEN', null)).toBe('进行中')
    expect(isBilibiliLiveSessionDurationUnknown('OPEN', null)).toBe(false)
    expect(isBilibiliLiveSessionDurationUnknown('INCOMPLETE', '2026-08-16T13:00:00+08:00')).toBe(false)
  })
})
