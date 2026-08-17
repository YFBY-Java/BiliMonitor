import { describe, expect, it } from 'vitest'

import widgetSource from './BilibiliLiveDanmuWidget.vue?raw'

describe('Bilibili live danmaku sender identity', () => {
  it('shows the full nickname and UID in a wider fixed sender column', () => {
    expect(widgetSource).toContain('formatDanmuSender(message)')
    expect(widgetSource).toContain(':title="formatDanmuSender(message)"')
    expect(widgetSource).toContain('grid-template-columns: 220px minmax(0, 1fr) max-content;')
    expect(widgetSource).toContain('text-overflow: ellipsis;')
    expect(widgetSource).toContain('white-space: nowrap;')
  })
})
