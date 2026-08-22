import { describe, expect, it } from 'vitest'

import source from './AnalyticsView.vue?raw'

describe('single-session analytics dashboard', () => {
  it('shows decision metrics, time-aligned signals, segments, gift mix, and findings', () => {
    expect(source).toContain('弹幕速率')
    expect(source).toContain('付费转化率')
    expect(source).toContain('ARPPU')
    expect(source).toContain('Top 5 收入占比')
    expect(source).toContain('互动与付费信号轨')
    expect(source).toContain('用户分层')
    expect(source).toContain('礼物结构')
    expect(source).toContain('规则洞察')
    expect(source).toContain('弹幕参与深度')
    expect(source).toContain('已识别人均弹幕')
    expect(source).toContain('重复互动率')
    expect(source).toContain('持续参与率')
    expect(source).toContain('高频弹幕')
    expect(source).toContain('付费用户深度')
    expect(source).toContain('场内复购率')
    expect(source).toContain('互动付费者')
    expect(source).toContain('历史复购者')
    expect(source).toContain('消费层级')
  })

  it('offers a fixed bucket selector and user-controlled refresh interval', () => {
    expect(source).toContain(':value="60"')
    expect(source).toContain(':value="300"')
    expect(source).toContain(':value="900"')
    expect(source).toContain('useSessionAutoRefresh')
    expect(source).toContain('立即刷新')
  })
})
