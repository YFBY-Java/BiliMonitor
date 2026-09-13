import { describe, expect, it } from 'vitest'

import viewSource from './BilibiliLiveView.vue?raw'
import widgetSource from '../subjects/widgets/BilibiliLiveDanmuWidget.vue?raw'

describe('live monitor danmaku panel', () => {
  it('reuses the existing widget before session statistics and isolates room switches', () => {
    expect(viewSource).toContain("import BilibiliLiveDanmuWidget from '@/views/subjects/widgets/BilibiliLiveDanmuWidget.vue'")
    expect(viewSource).toMatch(/<BilibiliLiveDanmuWidget\s+:key="expandedRoom.id"\s+:workbench="expandedDanmuWorkbench"/)
    expect(viewSource.indexOf('<BilibiliLiveDanmuWidget')).toBeLessThan(viewSource.indexOf('<BilibiliLiveSessionPanel'))
    expect(viewSource).toContain('monitorId: expandedRoom.value.id')
    expect(viewSource).not.toContain('new WebSocket(')
  })

  it('only requires room status and danmaku context, remaining compatible with the user workbench', () => {
    expect(widgetSource).toContain("danmu: SubjectWorkbench['danmu']")
    expect(widgetSource).toContain("Pick<NonNullable<SubjectWorkbench['bilibiliLiveRoom']>, 'monitorId' | 'liveStatus'>")
  })
})
