import { describe, expect, it } from 'vitest'

import source from './DataCenterView.vue?raw'

describe('Data Center live-session workspace', () => {
  it('replaces placeholder tables with session, event, user, and quality views', () => {
    expect(source).toContain('场次')
    expect(source).toContain('事件明细')
    expect(source).toContain('用户')
    expect(source).toContain('数据质量')
    expect(source).not.toContain('PlaceholderTable')
  })

  it('supports bounded filtering, paging, export, refresh, and analysis navigation', () => {
    expect(source).toContain('fetchBilibiliLiveSessionEvents')
    expect(source).toContain('el-pagination')
    expect(source).toContain('Excel XLSX')
    expect(source).toContain('去分析看板')
    expect(source).toContain('立即刷新')
  })
})
